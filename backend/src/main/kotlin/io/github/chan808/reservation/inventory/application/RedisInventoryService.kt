package io.github.chan808.reservation.inventory.application

import io.github.chan808.reservation.common.ErrorCode
import io.github.chan808.reservation.common.ProductException
import io.github.chan808.reservation.inventory.api.InventoryApi
import io.github.chan808.reservation.inventory.api.StockReservationCommand
import io.github.chan808.reservation.inventory.api.StockReservationResult
import io.github.chan808.reservation.product.stock.ProductStockPort
import io.github.chan808.reservation.product.stock.ProductStockSnapshot
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId

@Service
@ConditionalOnProperty(name = ["app.inventory.mode"], havingValue = "redis")
@Transactional(readOnly = true)
class RedisInventoryService(
    private val redisTemplate: StringRedisTemplate,
    private val productStockPort: ProductStockPort,
) : InventoryApi {

    companion object {
        private const val STOCK_FIELD = "stockQuantity"

        val RESERVE_SCRIPT = DefaultRedisScript(
            """
            local key = KEYS[1]
            if redis.call('EXISTS', key) == 0 then
                return 'MISSING'
            end

            local nowMs = tonumber(ARGV[1])
            local quantity = tonumber(ARGV[2])
            local saleStartAtMs = tonumber(redis.call('HGET', key, 'saleStartAtMs'))
            local saleEndAtMs = redis.call('HGET', key, 'saleEndAtMs')
            local stock = tonumber(redis.call('HGET', key, 'stockQuantity'))
            local price = tonumber(redis.call('HGET', key, 'price'))

            if saleStartAtMs ~= nil and nowMs < saleStartAtMs then
                return 'PRODUCT_NOT_OPEN'
            end

            if saleEndAtMs and saleEndAtMs ~= '' and nowMs >= tonumber(saleEndAtMs) then
                return 'PRODUCT_SALE_ENDED'
            end

            if stock == nil then
                return 'MISSING'
            end

            if stock < quantity then
                return 'PRODUCT_SOLD_OUT'
            end

            local remaining = redis.call('HINCRBY', key, 'stockQuantity', -quantity)
            return 'OK:' .. tostring(remaining) .. ':' .. tostring(price)
            """.trimIndent(),
            String::class.java,
        )

        val RELEASE_SCRIPT = DefaultRedisScript(
            """
            local key = KEYS[1]
            if redis.call('EXISTS', key) == 0 then
                return 'MISSING'
            end

            local quantity = tonumber(ARGV[1])
            local remaining = redis.call('HINCRBY', key, 'stockQuantity', quantity)
            return 'OK:' .. tostring(remaining)
            """.trimIndent(),
            String::class.java,
        )

        val INITIALIZE_SCRIPT = DefaultRedisScript(
            """
            local key = KEYS[1]
            if redis.call('EXISTS', key) == 1 then
                return 'EXISTS'
            end

            redis.call(
                'HSET',
                key,
                'stockQuantity', ARGV[1],
                'price', ARGV[2],
                'saleStartAtMs', ARGV[3],
                'saleEndAtMs', ARGV[4]
            )
            return 'CREATED'
            """.trimIndent(),
            String::class.java,
        )
    }

    @Transactional
    override fun reserveStock(command: StockReservationCommand): StockReservationResult {
        ensureLoaded(command.productId)
        val productKey = RedisInventoryKeys.productKey(command.productId)
        val raw = redisTemplate.execute(
            RESERVE_SCRIPT,
            listOf(productKey),
            System.currentTimeMillis().toString(),
            command.quantity.toString(),
        ) ?: "MISSING"

        if (raw == "MISSING") {
            ensureLoaded(command.productId, forceReload = true)
            return reserveStock(command)
        }

        val result = parseReserveResult(raw)
        markDirty(command.productId)
        return StockReservationResult(
            productId = command.productId,
            reservedQuantity = command.quantity,
            unitPrice = result.unitPrice,
            remainingStock = result.remainingStock,
        )
    }

    @Transactional
    override fun releaseStock(productId: Long, quantity: Int) {
        ensureLoaded(productId)
        val productKey = RedisInventoryKeys.productKey(productId)
        val raw = redisTemplate.execute(
            RELEASE_SCRIPT,
            listOf(productKey),
            quantity.toString(),
        ) ?: "MISSING"

        if (raw == "MISSING") {
            ensureLoaded(productId, forceReload = true)
            releaseStock(productId, quantity)
            return
        }

        if (!raw.startsWith("OK:")) {
            throw ProductException(ErrorCode.PRODUCT_NOT_FOUND)
        }
        markDirty(productId)
    }

    internal fun syncDirtyProduct(productId: Long) {
        val stock = redisTemplate.opsForHash()
            .get(RedisInventoryKeys.productKey(productId), STOCK_FIELD)
            ?.toString()
            ?.toIntOrNull()
            ?: return
        productStockPort.updateAvailableStock(productId, stock)
    }

    private fun ensureLoaded(productId: Long, forceReload: Boolean = false) {
        val productKey = RedisInventoryKeys.productKey(productId)
        if (!forceReload && redisTemplate.hasKey(productKey) == true) {
            return
        }

        val snapshot = productStockPort.findSnapshot(productId)
            ?: throw ProductException(ErrorCode.PRODUCT_NOT_FOUND)

        initializeSnapshot(snapshot)
    }

    private fun initializeSnapshot(snapshot: ProductStockSnapshot) {
        redisTemplate.execute(
            INITIALIZE_SCRIPT,
            listOf(RedisInventoryKeys.productKey(snapshot.productId)),
            snapshot.availableStock.toString(),
            snapshot.unitPrice.toString(),
            snapshot.saleStartAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli().toString(),
            snapshot.saleEndAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()?.toString() ?: "",
        )
    }

    private fun parseReserveResult(raw: String): RedisReserveResult =
        when {
            raw == ErrorCode.PRODUCT_NOT_OPEN.name || raw == "PRODUCT_NOT_OPEN" ->
                throw ProductException(ErrorCode.PRODUCT_NOT_OPEN)
            raw == ErrorCode.PRODUCT_SALE_ENDED.name || raw == "PRODUCT_SALE_ENDED" ->
                throw ProductException(ErrorCode.PRODUCT_SALE_ENDED)
            raw == ErrorCode.PRODUCT_SOLD_OUT.name || raw == "PRODUCT_SOLD_OUT" ->
                throw ProductException(ErrorCode.PRODUCT_SOLD_OUT)
            raw.startsWith("OK:") -> {
                val tokens = raw.split(":")
                if (tokens.size != 3) {
                    throw ProductException(ErrorCode.PRODUCT_NOT_FOUND)
                }
                RedisReserveResult(
                    remainingStock = tokens[1].toInt(),
                    unitPrice = tokens[2].toLong(),
                )
            }
            else -> throw ProductException(ErrorCode.PRODUCT_NOT_FOUND)
        }

    private fun markDirty(productId: Long) {
        redisTemplate.opsForSet().add(RedisInventoryKeys.DIRTY_PRODUCTS_KEY, productId.toString())
    }
}

private data class RedisReserveResult(
    val remainingStock: Int,
    val unitPrice: Long,
)

package io.github.chan808.reservation.inventory.application

import io.github.chan808.reservation.product.stock.ProductStockPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@ConditionalOnProperty(name = ["app.inventory.mode"], havingValue = "redis")
class RedisInventoryPersistenceService(
    private val redisTemplate: StringRedisTemplate,
    private val productStockPort: ProductStockPort,
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun syncDirtyProduct(productId: Long) {
        val stock = redisTemplate.opsForHash<String, String>()
            .get(RedisInventoryKeys.productKey(productId), "stockQuantity")
            ?.toIntOrNull()
            ?: return
        productStockPort.updateAvailableStock(productId, stock)
    }
}

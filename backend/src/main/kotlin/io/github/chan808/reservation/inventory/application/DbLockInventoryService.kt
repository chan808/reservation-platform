package io.github.chan808.reservation.inventory.application

import io.github.chan808.reservation.common.ErrorCode
import io.github.chan808.reservation.common.ProductException
import io.github.chan808.reservation.inventory.api.InventoryApi
import io.github.chan808.reservation.inventory.api.StockReservationCommand
import io.github.chan808.reservation.inventory.api.StockReservationResult
import io.github.chan808.reservation.product.stock.ProductStockPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@ConditionalOnProperty(name = ["app.inventory.mode"], havingValue = "db-lock", matchIfMissing = true)
@Transactional(readOnly = true)
class DbLockInventoryService(
    private val productStockPort: ProductStockPort,
) : InventoryApi {

    @Transactional
    override fun reserveStock(command: StockReservationCommand): StockReservationResult {
        val productStock = productStockPort.findByIdForUpdate(command.productId)
            ?: throw ProductException(ErrorCode.PRODUCT_NOT_FOUND)
        productStock.reserve(command.quantity, LocalDateTime.now())
        return StockReservationResult(
            productId = productStock.productId,
            reservedQuantity = command.quantity,
            unitPrice = productStock.unitPrice,
            remainingStock = productStock.remainingStock,
        )
    }

    @Transactional
    override fun releaseStock(productId: Long, quantity: Int) {
        val productStock = productStockPort.findByIdForUpdate(productId)
            ?: throw ProductException(ErrorCode.PRODUCT_NOT_FOUND)
        productStock.release(quantity, LocalDateTime.now())
    }
}

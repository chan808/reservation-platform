package io.github.chan808.reservation.product.api

import java.time.LocalDateTime

interface ProductApi {
    fun getSaleProduct(productId: Long): ProductSaleView
    fun reserveStock(command: StockReservationCommand): StockReservationResult
    fun releaseStock(productId: Long, quantity: Int)
}

data class ProductSaleView(
    val id: Long,
    val name: String,
    val price: Long,
    val status: ProductSaleStatus,
    val availableStock: Int,
    val saleStartAt: LocalDateTime,
    val saleEndAt: LocalDateTime?,
)

data class StockReservationCommand(
    val productId: Long,
    val quantity: Int,
)

data class StockReservationResult(
    val productId: Long,
    val reservedQuantity: Int,
    val remainingStock: Int,
)

enum class ProductSaleStatus {
    SCHEDULED,
    ON_SALE,
    SOLD_OUT,
    ENDED,
}

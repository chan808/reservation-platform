package io.github.chan808.reservation.product.stock

import java.time.LocalDateTime

interface ProductStockPort {
    fun findSnapshot(productId: Long): ProductStockSnapshot?
    fun findByIdForUpdate(productId: Long): ManagedProductStock?
    fun updateAvailableStock(productId: Long, stockQuantity: Int)
}

interface ManagedProductStock {
    val productId: Long
    val unitPrice: Long
    val remainingStock: Int

    fun reserve(quantity: Int, now: LocalDateTime)
    fun release(quantity: Int, now: LocalDateTime)
}

data class ProductStockSnapshot(
    val productId: Long,
    val unitPrice: Long,
    val availableStock: Int,
    val saleStartAt: LocalDateTime,
    val saleEndAt: LocalDateTime?,
)

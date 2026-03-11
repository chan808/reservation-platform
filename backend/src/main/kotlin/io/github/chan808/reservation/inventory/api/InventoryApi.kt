package io.github.chan808.reservation.inventory.api

interface InventoryApi {
    fun reserveStock(command: StockReservationCommand): StockReservationResult
    fun releaseStock(productId: Long, quantity: Int)
}

data class StockReservationCommand(
    val productId: Long,
    val quantity: Int,
)

data class StockReservationResult(
    val productId: Long,
    val reservedQuantity: Int,
    val unitPrice: Long,
    val remainingStock: Int,
)

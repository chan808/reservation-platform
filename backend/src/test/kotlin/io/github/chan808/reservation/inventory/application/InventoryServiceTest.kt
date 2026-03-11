package io.github.chan808.reservation.inventory.application

import io.github.chan808.reservation.common.ErrorCode
import io.github.chan808.reservation.common.ProductException
import io.github.chan808.reservation.inventory.api.StockReservationCommand
import io.github.chan808.reservation.product.stock.ManagedProductStock
import io.github.chan808.reservation.product.stock.ProductStockPort
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals

class InventoryServiceTest {

    private val productStockPort: ProductStockPort = mockk()
    private val managedProductStock: ManagedProductStock = mockk()
    private val inventoryService = DbLockInventoryService(productStockPort)

    @Test
    fun `reserveStock delegates to product stock port`() {
        every { productStockPort.findByIdForUpdate(1L) } returns managedProductStock
        every { managedProductStock.productId } returns 1L
        every { managedProductStock.unitPrice } returns 1000L
        every { managedProductStock.remainingStock } returns 3
        every { managedProductStock.reserve(2, any<LocalDateTime>()) } just Runs

        val result = inventoryService.reserveStock(StockReservationCommand(1L, 2))

        assertEquals(1L, result.productId)
        assertEquals(2, result.reservedQuantity)
        assertEquals(1000L, result.unitPrice)
        assertEquals(3, result.remainingStock)
        verify { managedProductStock.reserve(2, any<LocalDateTime>()) }
    }

    @Test
    fun `releaseStock throws when product is missing`() {
        every { productStockPort.findByIdForUpdate(1L) } returns null

        val ex = assertThrows<ProductException> { inventoryService.releaseStock(1L, 1) }

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.errorCode)
    }
}

package io.github.chan808.reservation.product.infrastructure.persistence

import io.github.chan808.reservation.product.domain.Product
import io.github.chan808.reservation.product.stock.ManagedProductStock
import io.github.chan808.reservation.product.stock.ProductStockSnapshot
import io.github.chan808.reservation.product.stock.ProductStockPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class JpaProductStockPort(
    private val productRepository: ProductRepository,
) : ProductStockPort {

    override fun findSnapshot(productId: Long): ProductStockSnapshot? =
        productRepository.findById(productId).orElse(null)?.let { it.toSnapshot() }

    override fun findByIdForUpdate(productId: Long): ManagedProductStock? =
        productRepository.findByIdForUpdate(productId)?.let(::JpaManagedProductStock)

    @Transactional
    override fun updateAvailableStock(productId: Long, stockQuantity: Int) {
        productRepository.updateStockQuantity(productId, stockQuantity)
    }
}

private class JpaManagedProductStock(
    private val product: Product,
) : ManagedProductStock {
    override val productId: Long
        get() = product.id

    override val unitPrice: Long
        get() = product.price

    override val remainingStock: Int
        get() = product.stockQuantity

    override fun reserve(quantity: Int, now: LocalDateTime) {
        product.reserve(quantity, now)
    }

    override fun release(quantity: Int, now: LocalDateTime) {
        product.release(quantity, now)
    }
}

private fun Product.toSnapshot(): ProductStockSnapshot =
    ProductStockSnapshot(
        productId = id,
        unitPrice = price,
        availableStock = stockQuantity,
        saleStartAt = saleStartAt,
        saleEndAt = saleEndAt,
    )

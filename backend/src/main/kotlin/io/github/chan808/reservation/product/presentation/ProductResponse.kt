package io.github.chan808.reservation.product.presentation

import io.github.chan808.reservation.product.api.ProductSaleStatus
import io.github.chan808.reservation.product.domain.Product
import io.github.chan808.reservation.product.domain.ProductStatus
import java.time.LocalDateTime

data class ProductResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val price: Long,
    val stockQuantity: Int,
    val status: ProductSaleStatus,
    val saleStartAt: LocalDateTime,
    val saleEndAt: LocalDateTime?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(product: Product, now: LocalDateTime): ProductResponse = ProductResponse(
            id = product.id,
            name = product.name,
            description = product.description,
            price = product.price,
            stockQuantity = product.stockQuantity,
            status = product.saleStatus(now).toApiStatus(),
            saleStartAt = product.saleStartAt,
            saleEndAt = product.saleEndAt,
            createdAt = product.createdAt,
        )
    }
}

fun ProductStatus.toApiStatus(): ProductSaleStatus =
    when (this) {
        ProductStatus.SCHEDULED -> ProductSaleStatus.SCHEDULED
        ProductStatus.ON_SALE -> ProductSaleStatus.ON_SALE
        ProductStatus.SOLD_OUT -> ProductSaleStatus.SOLD_OUT
        ProductStatus.ENDED -> ProductSaleStatus.ENDED
    }

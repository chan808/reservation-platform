package io.github.chan808.reservation.product.api

import java.time.LocalDateTime

interface ProductApi {
    fun getSaleProduct(productId: Long): ProductSaleView
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

enum class ProductSaleStatus {
    SCHEDULED,
    ON_SALE,
    SOLD_OUT,
    ENDED,
}

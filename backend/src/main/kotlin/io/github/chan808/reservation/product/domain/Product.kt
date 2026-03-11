package io.github.chan808.reservation.product.domain

import io.github.chan808.reservation.common.BaseEntity
import io.github.chan808.reservation.common.ErrorCode
import io.github.chan808.reservation.common.ProductException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDateTime

@Entity
@Table(name = "products")
class Product(
    @Column(nullable = false, length = 150)
    var name: String,

    @Column(nullable = true, columnDefinition = "TEXT")
    var description: String? = null,

    @Column(nullable = false)
    var price: Long,

    @Column(nullable = false)
    var stockQuantity: Int,

    @Column(nullable = false)
    var saleStartAt: LocalDateTime,

    @Column(nullable = true)
    var saleEndAt: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ProductStatus,

    @Version
    @Column(nullable = false)
    var version: Long = 0L,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
) : BaseEntity() {

    init {
        if (saleEndAt != null && !saleEndAt!!.isAfter(saleStartAt)) {
            throw ProductException(ErrorCode.INVALID_PRODUCT_SALE_WINDOW)
        }
        require(price >= 0) { "price must not be negative" }
        require(stockQuantity >= 0) { "stockQuantity must not be negative" }
    }

    fun saleStatus(now: LocalDateTime): ProductStatus =
        when {
            saleEndAt != null && !now.isBefore(saleEndAt) -> ProductStatus.ENDED
            now.isBefore(saleStartAt) -> ProductStatus.SCHEDULED
            stockQuantity <= 0 -> ProductStatus.SOLD_OUT
            else -> ProductStatus.ON_SALE
        }

    fun reserve(quantity: Int, now: LocalDateTime) {
        require(quantity > 0) { "quantity must be positive" }
        when (saleStatus(now)) {
            ProductStatus.SCHEDULED -> throw ProductException(ErrorCode.PRODUCT_NOT_OPEN)
            ProductStatus.ENDED -> throw ProductException(ErrorCode.PRODUCT_SALE_ENDED)
            ProductStatus.SOLD_OUT -> throw ProductException(ErrorCode.PRODUCT_SOLD_OUT)
            ProductStatus.ON_SALE -> {}
        }
        if (stockQuantity < quantity) {
            throw ProductException(ErrorCode.PRODUCT_SOLD_OUT)
        }
        stockQuantity -= quantity
        status = saleStatus(now)
    }

    fun release(quantity: Int, now: LocalDateTime) {
        require(quantity > 0) { "quantity must be positive" }
        stockQuantity += quantity
        status = saleStatus(now)
    }

    companion object {
        fun initialStatus(
            saleStartAt: LocalDateTime,
            saleEndAt: LocalDateTime?,
            stockQuantity: Int,
            now: LocalDateTime,
        ): ProductStatus =
            when {
                saleEndAt != null && !now.isBefore(saleEndAt) -> ProductStatus.ENDED
                now.isBefore(saleStartAt) -> ProductStatus.SCHEDULED
                stockQuantity <= 0 -> ProductStatus.SOLD_OUT
                else -> ProductStatus.ON_SALE
            }
    }
}

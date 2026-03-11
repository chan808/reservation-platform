package io.github.chan808.reservation.product.application

import io.github.chan808.reservation.common.ErrorCode
import io.github.chan808.reservation.common.PageResponse
import io.github.chan808.reservation.common.ProductException
import io.github.chan808.reservation.product.api.ProductApi
import io.github.chan808.reservation.product.api.ProductSaleView
import io.github.chan808.reservation.product.api.StockReservationCommand
import io.github.chan808.reservation.product.api.StockReservationResult
import io.github.chan808.reservation.product.domain.Product
import io.github.chan808.reservation.product.infrastructure.persistence.ProductRepository
import io.github.chan808.reservation.product.presentation.CreateProductRequest
import io.github.chan808.reservation.product.presentation.ProductResponse
import io.github.chan808.reservation.product.presentation.toApiStatus
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class ProductService(
    private val productRepository: ProductRepository,
) : ProductApi {

    @Transactional
    fun create(request: CreateProductRequest): ProductResponse {
        val now = LocalDateTime.now()
        val product = productRepository.save(
            Product(
                name = request.name.trim(),
                description = request.description?.trim()?.ifBlank { null },
                price = request.price,
                stockQuantity = request.stockQuantity,
                saleStartAt = request.saleStartAt,
                saleEndAt = request.saleEndAt,
                status = Product.initialStatus(request.saleStartAt, request.saleEndAt, request.stockQuantity, now),
            ),
        )
        return ProductResponse.from(product, now)
    }

    fun list(page: Int, size: Int): PageResponse<ProductResponse> {
        val now = LocalDateTime.now()
        val result = productRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
        return PageResponse(
            content = result.content.map { ProductResponse.from(it, now) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            first = result.isFirst,
            last = result.isLast,
        )
    }

    fun get(productId: Long): ProductResponse = ProductResponse.from(getProduct(productId), LocalDateTime.now())

    override fun getSaleProduct(productId: Long): ProductSaleView {
        val now = LocalDateTime.now()
        val product = getProduct(productId)
        return ProductSaleView(
            id = product.id,
            name = product.name,
            price = product.price,
            status = product.saleStatus(now).toApiStatus(),
            availableStock = product.stockQuantity,
            saleStartAt = product.saleStartAt,
            saleEndAt = product.saleEndAt,
        )
    }

    @Transactional
    override fun reserveStock(command: StockReservationCommand): StockReservationResult {
        val product = getProductForUpdate(command.productId)
        product.reserve(command.quantity, LocalDateTime.now())
        return StockReservationResult(
            productId = product.id,
            reservedQuantity = command.quantity,
            remainingStock = product.stockQuantity,
        )
    }

    @Transactional
    override fun releaseStock(productId: Long, quantity: Int) {
        val product = getProductForUpdate(productId)
        product.release(quantity, LocalDateTime.now())
    }

    private fun getProduct(productId: Long): Product =
        productRepository.findById(productId).orElseThrow { ProductException(ErrorCode.PRODUCT_NOT_FOUND) }

    private fun getProductForUpdate(productId: Long): Product =
        productRepository.findByIdForUpdate(productId) ?: throw ProductException(ErrorCode.PRODUCT_NOT_FOUND)
}

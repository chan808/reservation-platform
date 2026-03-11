package io.github.chan808.reservation.product.application

import io.github.chan808.reservation.common.ErrorCode
import io.github.chan808.reservation.common.ProductException
import io.github.chan808.reservation.product.domain.Product
import io.github.chan808.reservation.product.domain.ProductStatus
import io.github.chan808.reservation.product.infrastructure.persistence.ProductRepository
import io.github.chan808.reservation.product.presentation.CreateProductRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals

class ProductServiceTest {

    private val productRepository: ProductRepository = mockk()
    private val productService = ProductService(productRepository)

    @Test
    fun `create derives scheduled status for future sale`() {
        val request = CreateProductRequest(
            name = "Limited Item",
            description = "desc",
            price = 1000,
            stockQuantity = 10,
            saleStartAt = LocalDateTime.now().plusHours(1),
            saleEndAt = null,
        )
        every { productRepository.save(any()) } answers { firstArg<Product>() }

        val response = productService.create(request)

        assertEquals(ProductStatus.SCHEDULED.name, response.status.name)
    }

    @Test
    fun `get throws when product is missing`() {
        every { productRepository.findById(1L) } returns Optional.empty()

        val ex = assertThrows<ProductException> { productService.get(1L) }

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, ex.errorCode)
    }
}

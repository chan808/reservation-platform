package io.github.chan808.reservation.product.presentation

import io.github.chan808.reservation.common.ApiResponse
import io.github.chan808.reservation.common.PageResponse
import io.github.chan808.reservation.product.application.ProductService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/products")
class ProductController(
    private val productService: ProductService,
) {

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    fun create(@RequestBody @Valid request: CreateProductRequest): ResponseEntity<ApiResponse<ProductResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(productService.create(request)))

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> =
        ResponseEntity.ok(ApiResponse.of(productService.list(page, size)))

    @GetMapping("/{productId}")
    fun get(@PathVariable productId: Long): ResponseEntity<ApiResponse<ProductResponse>> =
        ResponseEntity.ok(ApiResponse.of(productService.get(productId)))
}

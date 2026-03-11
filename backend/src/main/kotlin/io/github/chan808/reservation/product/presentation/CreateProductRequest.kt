package io.github.chan808.reservation.product.presentation

import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CreateProductRequest(
    @field:NotBlank
    @field:Size(max = 150)
    val name: String,

    @field:Size(max = 5000)
    val description: String?,

    @field:Min(0)
    val price: Long,

    @field:Min(0)
    val stockQuantity: Int,

    @field:NotNull
    @field:FutureOrPresent
    val saleStartAt: LocalDateTime,

    val saleEndAt: LocalDateTime?,
)

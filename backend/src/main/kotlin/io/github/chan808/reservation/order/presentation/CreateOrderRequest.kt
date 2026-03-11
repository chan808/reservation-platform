package io.github.chan808.reservation.order.presentation

import io.github.chan808.reservation.payment.api.PaymentMethodType
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CreateOrderRequest(
    @field:NotNull
    val productId: Long,

    @field:Min(1)
    val quantity: Int,

    @field:NotBlank
    @field:Size(max = 64)
    val orderRequestId: String,

    @field:NotNull
    val paymentType: PaymentMethodType,
)

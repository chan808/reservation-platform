package io.github.chan808.reservation.order.presentation

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class ConfirmOrderPaymentRequest(
    @field:NotBlank
    val paymentKey: String,

    @field:Min(0)
    val amount: Long,
)

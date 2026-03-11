package io.github.chan808.reservation.order.presentation

import jakarta.validation.constraints.Size

data class CancelOrderRequest(
    @field:Size(max = 255)
    val reason: String?,
)

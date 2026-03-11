package io.github.chan808.reservation.payment.events

import java.time.LocalDateTime

data class PaymentCanceledEvent(
    val orderId: Long,
    val paymentId: String,
    val reason: String,
    val canceledAt: LocalDateTime,
)

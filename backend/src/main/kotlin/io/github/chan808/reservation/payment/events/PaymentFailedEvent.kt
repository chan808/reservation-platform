package io.github.chan808.reservation.payment.events

import java.time.LocalDateTime

data class PaymentFailedEvent(
    val orderId: Long,
    val paymentId: String?,
    val reason: String,
    val failedAt: LocalDateTime,
)

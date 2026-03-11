package io.github.chan808.reservation.payment.events

import java.time.LocalDateTime

data class PaymentSucceededEvent(
    val orderId: Long,
    val paymentId: String,
    val approvedAt: LocalDateTime,
)

package io.github.chan808.reservation.payment.events

import java.time.LocalDateTime

data class PaymentResultEvent(
    val eventId: String,
    val eventType: PaymentResultEventType,
    val orderId: Long,
    val paymentId: String?,
    val reason: String?,
    val occurredAt: LocalDateTime,
)

enum class PaymentResultEventType {
    SUCCEEDED,
    FAILED,
    CANCELED,
}

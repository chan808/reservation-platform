package io.github.chan808.reservation.order.events

data class OrderPaymentRequestedEvent(
    val orderId: Long,
    val orderNumber: String,
    val memberId: Long,
    val paymentType: String,
    val amount: Long,
)

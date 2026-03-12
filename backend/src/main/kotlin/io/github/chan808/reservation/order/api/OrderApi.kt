package io.github.chan808.reservation.order.api

import java.time.LocalDateTime

interface OrderApi {
    fun getOrderPaymentView(orderId: Long): OrderPaymentView
}

data class OrderPaymentView(
    val orderId: Long,
    val orderNumber: String,
    val memberId: Long,
    val productId: Long,
    val quantity: Int,
    val totalPrice: Long,
    val status: OrderStatusView,
    val paymentDeadlineAt: LocalDateTime?,
)

enum class OrderStatusView {
    PENDING_PAYMENT,
    PAYMENT_PROCESSING,
    PAID,
    PAYMENT_FAILED,
    CANCELED,
    EXPIRED,
}

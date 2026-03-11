package io.github.chan808.reservation.order.presentation

import io.github.chan808.reservation.order.domain.Order
import io.github.chan808.reservation.order.domain.OrderStatus
import io.github.chan808.reservation.payment.api.PaymentExecutionResult
import io.github.chan808.reservation.payment.api.PaymentMethodType
import io.github.chan808.reservation.payment.api.PaymentStatusView
import java.time.LocalDateTime

data class OrderResponse(
    val id: Long,
    val orderNumber: String,
    val productId: Long,
    val quantity: Int,
    val unitPrice: Long,
    val totalPrice: Long,
    val status: OrderStatus,
    val paymentType: PaymentMethodType,
    val paymentStatus: PaymentStatusView?,
    val paymentId: String?,
    val paymentDeadlineAt: LocalDateTime?,
    val orderedAt: LocalDateTime,
    val canceledAt: LocalDateTime?,
    val cancelReason: String?,
) {
    companion object {
        fun from(order: Order, payment: PaymentExecutionResult?): OrderResponse = OrderResponse(
            id = order.id,
            orderNumber = order.orderNumber,
            productId = order.productId,
            quantity = order.quantity,
            unitPrice = order.unitPrice,
            totalPrice = order.totalPrice,
            status = order.status,
            paymentType = order.paymentType,
            paymentStatus = payment?.status,
            paymentId = payment?.paymentId,
            paymentDeadlineAt = order.paymentDeadlineAt,
            orderedAt = order.orderedAt,
            canceledAt = order.canceledAt,
            cancelReason = order.cancelReason,
        )
    }
}

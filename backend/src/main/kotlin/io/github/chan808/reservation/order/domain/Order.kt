package io.github.chan808.reservation.order.domain

import io.github.chan808.reservation.common.BaseEntity
import io.github.chan808.reservation.common.ErrorCode
import io.github.chan808.reservation.common.OrderException
import io.github.chan808.reservation.payment.api.PaymentMethodType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "orders")
class Order(
    @Column(nullable = false, length = 40)
    val orderNumber: String,

    @Column(nullable = false)
    val memberId: Long,

    @Column(nullable = false)
    val productId: Long,

    @Column(nullable = false, length = 64)
    val orderRequestId: String,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false)
    val unitPrice: Long,

    @Column(nullable = false)
    val totalPrice: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: OrderStatus,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val paymentType: PaymentMethodType,

    @Column(nullable = true)
    val paymentDeadlineAt: LocalDateTime? = null,

    @Column(nullable = false)
    val orderedAt: LocalDateTime,

    @Column(nullable = true)
    var canceledAt: LocalDateTime? = null,

    @Column(nullable = true, length = 255)
    var cancelReason: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
) : BaseEntity() {
    fun markPaymentProcessing() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw OrderException(ErrorCode.PAYMENT_CONFIRM_NOT_ALLOWED)
        }
        status = OrderStatus.PAYMENT_PROCESSING
    }

    fun markPaid() {
        status = OrderStatus.PAID
        cancelReason = null
        canceledAt = null
    }

    fun markPaymentFailed(reason: String) {
        status = OrderStatus.PAYMENT_FAILED
        cancelReason = reason
    }

    fun cancel(reason: String, now: LocalDateTime) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw OrderException(ErrorCode.ORDER_CANNOT_CANCEL)
        }
        status = OrderStatus.CANCELED
        canceledAt = now
        cancelReason = reason
    }

    fun markPaymentCanceled(reason: String, now: LocalDateTime) {
        if (status == OrderStatus.CANCELED) return
        if (status !in setOf(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_PROCESSING)) {
            throw OrderException(ErrorCode.ORDER_CANNOT_CANCEL)
        }
        status = OrderStatus.CANCELED
        canceledAt = now
        cancelReason = reason
    }

    fun expire(now: LocalDateTime) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw OrderException(ErrorCode.ORDER_CANNOT_CANCEL)
        }
        status = OrderStatus.EXPIRED
        canceledAt = now
        cancelReason = "ORDER_EXPIRED"
    }
}

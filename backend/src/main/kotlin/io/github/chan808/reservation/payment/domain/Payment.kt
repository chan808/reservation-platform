package io.github.chan808.reservation.payment.domain

import io.github.chan808.reservation.common.BaseEntity
import io.github.chan808.reservation.common.ErrorCode
import io.github.chan808.reservation.common.PaymentException
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
@Table(name = "payments")
class Payment(
    @Column(nullable = false)
    val orderId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val paymentType: PaymentMethodType,

    @Column(nullable = true, length = 200)
    var paymentKey: String? = null,

    @Column(nullable = true, length = 100)
    var providerOrderId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: PaymentStatus,

    @Column(nullable = false)
    val amount: Long,

    @Column(nullable = true)
    var approvedAmount: Long? = null,

    @Column(nullable = true)
    var canceledAmount: Long? = null,

    @Column(nullable = true, length = 100)
    var failureCode: String? = null,

    @Column(nullable = true, length = 255)
    var failureMessage: String? = null,

    @Column(nullable = true, columnDefinition = "json")
    var rawResponse: String? = null,

    @Column(nullable = false)
    val requestedAt: LocalDateTime,

    @Column(nullable = true)
    var approvedAt: LocalDateTime? = null,

    @Column(nullable = true)
    var failedAt: LocalDateTime? = null,

    @Column(nullable = true)
    var canceledAt: LocalDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
) : BaseEntity() {
    fun markSucceeded(paymentId: String?, now: LocalDateTime) {
        if (status == PaymentStatus.SUCCEEDED) return
        paymentId?.let { paymentKey = it }
        status = PaymentStatus.SUCCEEDED
        approvedAmount = amount
        approvedAt = now
        failureCode = null
        failureMessage = null
    }

    fun markFailed(reason: String, now: LocalDateTime) {
        if (status == PaymentStatus.FAILED) return
        status = PaymentStatus.FAILED
        failureMessage = reason
        failedAt = now
    }

    fun cancel(reason: String, now: LocalDateTime) {
        if (status == PaymentStatus.CANCELED) return
        if (status !in setOf(PaymentStatus.READY, PaymentStatus.PENDING)) {
            throw PaymentException(ErrorCode.PAYMENT_CANNOT_CANCEL)
        }
        status = PaymentStatus.CANCELED
        canceledAmount = amount
        failureMessage = reason
        canceledAt = now
    }
}

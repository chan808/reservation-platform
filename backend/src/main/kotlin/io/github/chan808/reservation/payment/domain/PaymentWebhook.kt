package io.github.chan808.reservation.payment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "payment_webhooks")
class PaymentWebhook(
    @Column(nullable = true)
    var paymentId: Long? = null,

    @Column(nullable = false, length = 20)
    val provider: String,

    @Column(nullable = true, length = 100)
    val externalEventId: String? = null,

    @Column(nullable = false, length = 100)
    val eventType: String,

    @Column(nullable = false, columnDefinition = "json")
    val payload: String,

    @Column(nullable = false)
    var processed: Boolean = false,

    @Column(nullable = true)
    var processedAt: LocalDateTime? = null,

    @Column(nullable = true, length = 255)
    var processingError: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
) {
    fun markProcessed(now: LocalDateTime) {
        processed = true
        processedAt = now
        processingError = null
    }

    fun markFailed(message: String) {
        processed = false
        processingError = message.take(255)
    }
}

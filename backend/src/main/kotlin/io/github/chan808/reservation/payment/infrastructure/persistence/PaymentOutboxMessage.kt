package io.github.chan808.reservation.payment.infrastructure.persistence

import io.github.chan808.reservation.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "payment_outbox_messages")
class PaymentOutboxMessage(
    @Column(nullable = false, length = 64, unique = true)
    val eventId: String,

    @Column(nullable = false, length = 120)
    val topic: String,

    @Column(nullable = false, length = 120)
    val messageKey: String,

    @Column(nullable = false, length = 60)
    val eventType: String,

    @Column(nullable = false, columnDefinition = "json")
    val payload: String,

    @Column(nullable = false)
    var published: Boolean = false,

    @Column(nullable = false)
    var attemptCount: Int = 0,

    @Column(nullable = true, length = 255)
    var lastError: String? = null,

    @Column(nullable = true)
    var publishedAt: LocalDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
) : BaseEntity() {

    fun markPublished(now: LocalDateTime) {
        published = true
        publishedAt = now
        lastError = null
    }

    fun markFailed(message: String) {
        attemptCount += 1
        lastError = message.take(255)
    }
}

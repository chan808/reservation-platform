package io.github.chan808.reservation.payment.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface PaymentOutboxRepository : JpaRepository<PaymentOutboxMessage, Long> {
    fun findTop100ByPublishedFalseAndAttemptCountLessThanOrderByCreatedAtAsc(maxAttempts: Int): List<PaymentOutboxMessage>
}

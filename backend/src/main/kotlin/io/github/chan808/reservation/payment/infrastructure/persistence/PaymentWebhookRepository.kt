package io.github.chan808.reservation.payment.infrastructure.persistence

import io.github.chan808.reservation.payment.domain.PaymentWebhook
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentWebhookRepository : JpaRepository<PaymentWebhook, Long> {
    fun findByProviderAndExternalEventId(provider: String, externalEventId: String): PaymentWebhook?
}

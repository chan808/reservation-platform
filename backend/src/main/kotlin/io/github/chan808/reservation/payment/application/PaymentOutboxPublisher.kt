package io.github.chan808.reservation.payment.application

import io.github.chan808.reservation.payment.infrastructure.persistence.PaymentOutboxMessage
import io.github.chan808.reservation.payment.infrastructure.persistence.PaymentOutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(name = ["app.payment.kafka.enabled"], havingValue = "true", matchIfMissing = true)
class PaymentOutboxPublisher(
    private val paymentOutboxRepository: PaymentOutboxRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${app.payment.kafka.max-attempts:20}") private val maxAttempts: Int,
    @Value("\${app.payment.kafka.publisher-batch-size:100}") private val batchSize: Int,
) {

    private val log = LoggerFactory.getLogger(PaymentOutboxPublisher::class.java)

    @Scheduled(
        fixedDelayString = "\${app.payment.kafka.publisher-delay-ms:500}",
        initialDelayString = "\${app.payment.kafka.publisher-delay-ms:500}",
    )
    fun publishPendingMessages() {
        val messages = paymentOutboxRepository
            .findTop100ByPublishedFalseAndAttemptCountLessThanOrderByCreatedAtAsc(maxAttempts)
            .take(batchSize)

        messages.forEach(::publishSingle)
    }

    @Transactional
    fun publishSingle(message: PaymentOutboxMessage) {
        try {
            kafkaTemplate.send(message.topic, message.messageKey, message.payload)
                .get(5, TimeUnit.SECONDS)
            message.markPublished(LocalDateTime.now())
        } catch (ex: Exception) {
            message.markFailed(ex.message ?: "Kafka publish failed")
            log.warn("[PAYMENT] failed to publish outbox eventId={} topic={}", message.eventId, message.topic, ex)
        }
        paymentOutboxRepository.save(message)
    }
}

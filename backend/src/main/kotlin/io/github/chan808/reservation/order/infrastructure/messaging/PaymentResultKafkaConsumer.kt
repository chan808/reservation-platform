package io.github.chan808.reservation.order.infrastructure.messaging

import io.github.chan808.reservation.order.application.PaymentResultEventProcessor
import io.github.chan808.reservation.payment.events.PaymentResultEvent
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
@ConditionalOnProperty(name = ["app.payment.kafka.enabled"], havingValue = "true", matchIfMissing = true)
class PaymentResultKafkaConsumer(
    private val objectMapper: ObjectMapper,
    private val paymentResultEventProcessor: PaymentResultEventProcessor,
) {

    @KafkaListener(
        topics = ["\${app.payment.kafka.topic:payment-result.v1}"],
        groupId = "\${app.payment.kafka.consumer-group:order-payment-result}",
    )
    fun consume(payload: String) {
        paymentResultEventProcessor.process(
            objectMapper.readValue(payload, PaymentResultEvent::class.java),
        )
    }
}

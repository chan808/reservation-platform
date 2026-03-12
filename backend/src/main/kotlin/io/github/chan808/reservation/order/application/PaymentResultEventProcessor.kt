package io.github.chan808.reservation.order.application

import io.github.chan808.reservation.inventory.api.InventoryApi
import io.github.chan808.reservation.order.domain.OrderStatus
import io.github.chan808.reservation.order.domain.OrderStatusHistory
import io.github.chan808.reservation.order.infrastructure.persistence.OrderRepository
import io.github.chan808.reservation.order.infrastructure.persistence.OrderStatusHistoryRepository
import io.github.chan808.reservation.order.infrastructure.persistence.ProcessedKafkaMessage
import io.github.chan808.reservation.order.infrastructure.persistence.ProcessedKafkaMessageRepository
import io.github.chan808.reservation.payment.events.PaymentResultEvent
import io.github.chan808.reservation.payment.events.PaymentResultEventType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentResultEventProcessor(
    private val orderRepository: OrderRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
    private val processedKafkaMessageRepository: ProcessedKafkaMessageRepository,
    private val inventoryApi: InventoryApi,
    @Value("\${app.payment.kafka.consumer-group:order-payment-result}") private val consumerGroup: String,
) {

    private val log = LoggerFactory.getLogger(PaymentResultEventProcessor::class.java)

    @Transactional
    fun process(event: PaymentResultEvent) {
        try {
            processedKafkaMessageRepository.save(
                ProcessedKafkaMessage(
                    consumerGroup = consumerGroup,
                    eventId = event.eventId,
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            log.info("[ORDER] skip duplicate payment result eventId={}", event.eventId)
            return
        }

        val order = orderRepository.findByIdForUpdate(event.orderId) ?: return
        if (order.status !in setOf(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_PROCESSING)) {
            return
        }
        val fromStatus = order.status.name

        when (event.eventType) {
            PaymentResultEventType.SUCCEEDED -> {
                order.markPaid()
                orderStatusHistoryRepository.save(
                    OrderStatusHistory(
                        orderId = order.id,
                        fromStatus = fromStatus,
                        toStatus = order.status.name,
                        reason = "PAYMENT_SUCCEEDED",
                        actorType = "PAYMENT_EVENT",
                        actorId = event.paymentId,
                    ),
                )
            }
            PaymentResultEventType.FAILED -> {
                val reason = event.reason ?: "PAYMENT_CONFIRM_FAILED"
                order.markPaymentFailed(reason)
                inventoryApi.releaseStock(order.productId, order.quantity)
                orderStatusHistoryRepository.save(
                    OrderStatusHistory(
                        orderId = order.id,
                        fromStatus = fromStatus,
                        toStatus = order.status.name,
                        reason = reason,
                        actorType = "PAYMENT_EVENT",
                        actorId = event.paymentId,
                    ),
                )
            }
            PaymentResultEventType.CANCELED -> {
                val reason = event.reason ?: "PAYMENT_CANCELED"
                order.markPaymentCanceled(reason, event.occurredAt)
                inventoryApi.releaseStock(order.productId, order.quantity)
                orderStatusHistoryRepository.save(
                    OrderStatusHistory(
                        orderId = order.id,
                        fromStatus = fromStatus,
                        toStatus = order.status.name,
                        reason = reason,
                        actorType = "PAYMENT_EVENT",
                        actorId = event.paymentId,
                    ),
                )
            }
        }
    }
}

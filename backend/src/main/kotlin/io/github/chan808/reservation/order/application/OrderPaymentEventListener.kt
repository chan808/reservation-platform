package io.github.chan808.reservation.order.application

import io.github.chan808.reservation.common.ErrorCode
import io.github.chan808.reservation.common.OrderException
import io.github.chan808.reservation.order.domain.OrderStatus
import io.github.chan808.reservation.order.domain.OrderStatusHistory
import io.github.chan808.reservation.order.infrastructure.persistence.OrderRepository
import io.github.chan808.reservation.order.infrastructure.persistence.OrderStatusHistoryRepository
import io.github.chan808.reservation.payment.events.PaymentCanceledEvent
import io.github.chan808.reservation.payment.events.PaymentFailedEvent
import io.github.chan808.reservation.payment.events.PaymentSucceededEvent
import io.github.chan808.reservation.product.api.ProductApi
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
@Transactional
class OrderPaymentEventListener(
    private val orderRepository: OrderRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
    private val productApi: ProductApi,
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentSucceeded(event: PaymentSucceededEvent) {
        val order = orderRepository.findById(event.orderId).orElseThrow { OrderException(ErrorCode.ORDER_NOT_FOUND) }
        if (order.status == OrderStatus.PENDING_PAYMENT) {
            order.markPaid()
            orderStatusHistoryRepository.save(
                OrderStatusHistory(
                    orderId = order.id,
                    fromStatus = OrderStatus.PENDING_PAYMENT.name,
                    toStatus = order.status.name,
                    reason = "PAYMENT_SUCCEEDED",
                    actorType = "PAYMENT",
                    actorId = event.paymentId,
                ),
)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentFailed(event: PaymentFailedEvent) {
        val order = orderRepository.findById(event.orderId).orElseThrow { OrderException(ErrorCode.ORDER_NOT_FOUND) }
        if (order.status == OrderStatus.PENDING_PAYMENT) {
            order.markPaymentFailed(event.reason)
            productApi.releaseStock(order.productId, order.quantity)
            orderStatusHistoryRepository.save(
                OrderStatusHistory(
                    orderId = order.id,
                    fromStatus = OrderStatus.PENDING_PAYMENT.name,
                    toStatus = order.status.name,
                    reason = event.reason,
                    actorType = "PAYMENT",
                    actorId = event.paymentId,
                ),
            )
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onPaymentCanceled(event: PaymentCanceledEvent) {
        val order = orderRepository.findById(event.orderId).orElseThrow { OrderException(ErrorCode.ORDER_NOT_FOUND) }
        if (order.status == OrderStatus.PENDING_PAYMENT) {
            order.cancel(event.reason, event.canceledAt)
            productApi.releaseStock(order.productId, order.quantity)
            orderStatusHistoryRepository.save(
                OrderStatusHistory(
                    orderId = order.id,
                    fromStatus = OrderStatus.PENDING_PAYMENT.name,
                    toStatus = order.status.name,
                    reason = event.reason,
                    actorType = "PAYMENT",
                    actorId = event.paymentId,
                ),
            )
        }
    }
}

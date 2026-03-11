package io.github.chan808.reservation.order.application

import io.github.chan808.reservation.common.ErrorCode
import io.github.chan808.reservation.common.MemberException
import io.github.chan808.reservation.common.OrderException
import io.github.chan808.reservation.member.api.MemberApi
import io.github.chan808.reservation.order.api.OrderApi
import io.github.chan808.reservation.order.api.OrderPaymentView
import io.github.chan808.reservation.order.api.OrderStatusView
import io.github.chan808.reservation.order.domain.Order
import io.github.chan808.reservation.order.domain.OrderStatus
import io.github.chan808.reservation.order.domain.OrderStatusHistory
import io.github.chan808.reservation.order.events.OrderPaymentRequestedEvent
import io.github.chan808.reservation.order.infrastructure.persistence.OrderRepository
import io.github.chan808.reservation.order.infrastructure.persistence.OrderStatusHistoryRepository
import io.github.chan808.reservation.order.presentation.CancelOrderRequest
import io.github.chan808.reservation.order.presentation.ConfirmOrderPaymentRequest
import io.github.chan808.reservation.order.presentation.CreateOrderRequest
import io.github.chan808.reservation.order.presentation.OrderResponse
import io.github.chan808.reservation.payment.api.PaymentApi
import io.github.chan808.reservation.payment.api.PaymentCommand
import io.github.chan808.reservation.payment.api.PaymentConfirmCommand
import io.github.chan808.reservation.payment.api.PaymentExecutionResult
import io.github.chan808.reservation.product.api.ProductApi
import io.github.chan808.reservation.product.api.StockReservationCommand
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
    private val memberApi: MemberApi,
    private val productApi: ProductApi,
    private val paymentApi: PaymentApi,
    private val eventPublisher: ApplicationEventPublisher,
) : OrderApi {

    @Transactional
    fun create(memberId: Long, request: CreateOrderRequest): OrderResponse {
        val member = memberApi.findAuthMemberById(memberId) ?: throw MemberException(ErrorCode.MEMBER_NOT_FOUND)

        orderRepository.findByOrderRequestId(request.orderRequestId)?.let { existing ->
            if (existing.memberId != memberId) {
                throw OrderException(ErrorCode.ORDER_REQUEST_CONFLICT)
            }
            return OrderResponse.from(existing, paymentApi.getPayment(existing.id))
        }

        if (orderRepository.existsByMemberIdAndProductIdAndStatusIn(memberId, request.productId, setOf(OrderStatus.PENDING_PAYMENT))) {
            throw OrderException(ErrorCode.DUPLICATE_ACTIVE_ORDER)
        }

        val reservation = productApi.reserveStock(StockReservationCommand(request.productId, request.quantity))
        val totalPrice = reservation.unitPrice * request.quantity

        val now = LocalDateTime.now()
        val order = orderRepository.save(
            Order(
                orderNumber = generateOrderNumber(),
                memberId = memberId,
                productId = request.productId,
                orderRequestId = request.orderRequestId,
                quantity = request.quantity,
                unitPrice = reservation.unitPrice,
                totalPrice = totalPrice,
                status = OrderStatus.PENDING_PAYMENT,
                paymentType = request.paymentType,
                paymentDeadlineAt = now.plusMinutes(15),
                orderedAt = now,
            ),
        )

        orderStatusHistoryRepository.save(
            OrderStatusHistory(
                orderId = order.id,
                toStatus = order.status.name,
                reason = "ORDER_CREATED",
                actorType = "MEMBER",
                actorId = memberId.toString(),
            ),
        )

        val payment = paymentApi.preparePayment(
            PaymentCommand(
                orderId = order.id,
                orderNumber = order.orderNumber,
                paymentType = request.paymentType,
                amount = order.totalPrice,
                buyerEmail = member.email,
            ),
        )

        eventPublisher.publishEvent(
            OrderPaymentRequestedEvent(
                orderId = order.id,
                orderNumber = order.orderNumber,
                memberId = memberId,
                paymentType = request.paymentType.name,
                amount = order.totalPrice,
            ),
        )

        return OrderResponse.from(order, payment)
    }

    fun get(memberId: Long, orderId: Long): OrderResponse {
        val order = getOwnedOrder(memberId, orderId)
        return OrderResponse.from(order, paymentApi.getPayment(order.id))
    }

    @Transactional
    fun cancel(memberId: Long, orderId: Long, request: CancelOrderRequest): OrderResponse {
        val order = getOwnedOrder(memberId, orderId)
        val reason = request.reason?.trim()?.ifBlank { null } ?: "ORDER_CANCELED_BY_MEMBER"
        order.cancel(reason, LocalDateTime.now())
        paymentApi.cancelPayment(order.id, reason)
        productApi.releaseStock(order.productId, order.quantity)
        orderStatusHistoryRepository.save(
            OrderStatusHistory(
                orderId = order.id,
                fromStatus = OrderStatus.PENDING_PAYMENT.name,
                toStatus = order.status.name,
                reason = reason,
                actorType = "MEMBER",
                actorId = memberId.toString(),
            ),
        )
        return OrderResponse.from(order, paymentApi.getPayment(order.id))
    }

    @Transactional
    fun confirmPayment(memberId: Long, orderId: Long, request: ConfirmOrderPaymentRequest): PaymentExecutionResult {
        val order = getOwnedOrder(memberId, orderId)
        if (order.status != OrderStatus.PENDING_PAYMENT) {
            throw OrderException(ErrorCode.PAYMENT_CONFIRM_NOT_ALLOWED)
        }
        if (order.totalPrice != request.amount) {
            throw OrderException(ErrorCode.PAYMENT_AMOUNT_MISMATCH)
        }
        val payment = paymentApi.confirmPayment(
            PaymentConfirmCommand(
                orderId = order.id,
                orderNumber = order.orderNumber,
                paymentKey = request.paymentKey,
                amount = request.amount,
            ),
        )

        when (payment.status) {
            io.github.chan808.reservation.payment.api.PaymentStatusView.SUCCEEDED -> {
                order.markPaid()
                orderStatusHistoryRepository.save(
                    OrderStatusHistory(
                        orderId = order.id,
                        fromStatus = OrderStatus.PENDING_PAYMENT.name,
                        toStatus = order.status.name,
                        reason = "PAYMENT_SUCCEEDED",
                        actorType = "PAYMENT",
                        actorId = payment.paymentId,
                    ),
                )
            }
            io.github.chan808.reservation.payment.api.PaymentStatusView.FAILED -> {
                val reason = payment.reason ?: "PAYMENT_CONFIRM_FAILED"
                order.markPaymentFailed(reason)
                productApi.releaseStock(order.productId, order.quantity)
                orderStatusHistoryRepository.save(
                    OrderStatusHistory(
                        orderId = order.id,
                        fromStatus = OrderStatus.PENDING_PAYMENT.name,
                        toStatus = order.status.name,
                        reason = reason,
                        actorType = "PAYMENT",
                        actorId = payment.paymentId,
                    ),
                )
            }
            io.github.chan808.reservation.payment.api.PaymentStatusView.CANCELED -> {
                val reason = payment.reason ?: "PAYMENT_CANCELED"
                order.cancel(reason, LocalDateTime.now())
                productApi.releaseStock(order.productId, order.quantity)
                orderStatusHistoryRepository.save(
                    OrderStatusHistory(
                        orderId = order.id,
                        fromStatus = OrderStatus.PENDING_PAYMENT.name,
                        toStatus = order.status.name,
                        reason = reason,
                        actorType = "PAYMENT",
                        actorId = payment.paymentId,
                    ),
                )
            }
            io.github.chan808.reservation.payment.api.PaymentStatusView.READY,
            io.github.chan808.reservation.payment.api.PaymentStatusView.PENDING -> {
                throw OrderException(ErrorCode.PAYMENT_CONFIRM_FAILED)
            }
        }

        return payment
    }

    @Transactional
    fun expireTimedOutOrders(now: LocalDateTime = LocalDateTime.now()): Int {
        val targets = orderRepository.findAllByStatusAndPaymentDeadlineAtBefore(OrderStatus.PENDING_PAYMENT, now)
        targets.forEach { order ->
            order.expire(now)
            paymentApi.cancelPayment(order.id, "ORDER_EXPIRED")
            productApi.releaseStock(order.productId, order.quantity)
            orderStatusHistoryRepository.save(
                OrderStatusHistory(
                    orderId = order.id,
                    fromStatus = OrderStatus.PENDING_PAYMENT.name,
                    toStatus = order.status.name,
                    reason = "ORDER_EXPIRED",
                    actorType = "SYSTEM",
                    actorId = null,
                ),
            )
        }
        return targets.size
    }

    override fun getOrderPaymentView(orderId: Long): OrderPaymentView {
        val order = orderRepository.findById(orderId).orElseThrow { OrderException(ErrorCode.ORDER_NOT_FOUND) }
        return OrderPaymentView(
            orderId = order.id,
            orderNumber = order.orderNumber,
            memberId = order.memberId,
            productId = order.productId,
            quantity = order.quantity,
            totalPrice = order.totalPrice,
            status = order.status.toView(),
            paymentDeadlineAt = order.paymentDeadlineAt,
        )
    }

    private fun getOwnedOrder(memberId: Long, orderId: Long): Order =
        orderRepository.findByIdAndMemberId(orderId, memberId) ?: throw OrderException(ErrorCode.ORDER_NOT_FOUND)

    private fun generateOrderNumber(): String = "ORD-${UUID.randomUUID().toString().replace("-", "").take(20)}"
}

private fun OrderStatus.toView(): OrderStatusView =
    when (this) {
        OrderStatus.PENDING_PAYMENT -> OrderStatusView.PENDING_PAYMENT
        OrderStatus.PAID -> OrderStatusView.PAID
        OrderStatus.PAYMENT_FAILED -> OrderStatusView.PAYMENT_FAILED
        OrderStatus.CANCELED -> OrderStatusView.CANCELED
        OrderStatus.EXPIRED -> OrderStatusView.EXPIRED
    }

package io.github.chan808.reservation.payment.application

import io.github.chan808.reservation.common.ErrorCode
import io.github.chan808.reservation.common.PaymentException
import io.github.chan808.reservation.payment.api.PaymentApi
import io.github.chan808.reservation.payment.api.PaymentCommand
import io.github.chan808.reservation.payment.api.PaymentConfirmCommand
import io.github.chan808.reservation.payment.api.PaymentExecutionResult
import io.github.chan808.reservation.payment.api.PaymentMethodType
import io.github.chan808.reservation.payment.api.PaymentStatusView
import io.github.chan808.reservation.payment.api.PaymentWebhookRequest
import io.github.chan808.reservation.payment.application.gateway.GatewayCancelCommand
import io.github.chan808.reservation.payment.application.gateway.GatewayConfirmCommand
import io.github.chan808.reservation.payment.application.gateway.PaymentGatewayRegistry
import io.github.chan808.reservation.payment.domain.Payment
import io.github.chan808.reservation.payment.domain.PaymentStatus
import io.github.chan808.reservation.payment.domain.PaymentWebhook
import io.github.chan808.reservation.payment.infrastructure.persistence.PaymentRepository
import io.github.chan808.reservation.payment.infrastructure.persistence.PaymentWebhookRepository
import io.github.chan808.reservation.payment.events.PaymentCanceledEvent
import io.github.chan808.reservation.payment.events.PaymentFailedEvent
import io.github.chan808.reservation.payment.events.PaymentSucceededEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentWebhookRepository: PaymentWebhookRepository,
    private val paymentGatewayRegistry: PaymentGatewayRegistry,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher,
) : PaymentApi {

    @Transactional
    override fun preparePayment(command: PaymentCommand): PaymentExecutionResult {
        val gatewayResult = paymentGatewayRegistry.get(command.paymentType).prepare(command)
        val payment = paymentRepository.save(
            Payment(
                orderId = command.orderId,
                paymentType = command.paymentType,
                paymentKey = gatewayResult.paymentId,
                providerOrderId = command.orderNumber,
                status = gatewayResult.status.toDomain(),
                amount = command.amount,
                rawResponse = gatewayResult.rawResponse,
                requestedAt = LocalDateTime.now(),
            ),
        )
        return payment.toResult(gatewayResult.redirectUrl)
    }

    @Transactional
    override fun confirmPayment(command: PaymentConfirmCommand): PaymentExecutionResult {
        val payment = paymentRepository.findByOrderId(command.orderId) ?: throw PaymentException(ErrorCode.PAYMENT_NOT_FOUND)
        val gatewayResult = paymentGatewayRegistry.get(payment.paymentType).confirm(
            GatewayConfirmCommand(
                paymentKey = command.paymentKey,
                orderNumber = command.orderNumber,
                amount = command.amount,
            ),
        )

        val now = LocalDateTime.now()
        when (gatewayResult.status) {
            PaymentStatusView.SUCCEEDED -> {
                payment.markSucceeded(gatewayResult.paymentId, now)
                payment.rawResponse = gatewayResult.rawResponse
                eventPublisher.publishEvent(PaymentSucceededEvent(payment.orderId, gatewayResult.paymentId, now))
            }
            PaymentStatusView.FAILED -> {
                payment.markFailed("PAYMENT_CONFIRM_FAILED", now)
                payment.rawResponse = gatewayResult.rawResponse
                eventPublisher.publishEvent(PaymentFailedEvent(payment.orderId, gatewayResult.paymentId, "PAYMENT_CONFIRM_FAILED", now))
            }
            PaymentStatusView.CANCELED -> {
                payment.cancel("PAYMENT_CANCELED", now)
                payment.rawResponse = gatewayResult.rawResponse
                eventPublisher.publishEvent(PaymentCanceledEvent(payment.orderId, gatewayResult.paymentId, "PAYMENT_CANCELED", now))
            }
            PaymentStatusView.READY, PaymentStatusView.PENDING -> throw PaymentException(ErrorCode.PAYMENT_CONFIRM_FAILED)
        }
        return paymentRepository.saveAndFlush(payment).toResult()
    }

    @Transactional
    override fun cancelPayment(orderId: Long, reason: String): PaymentExecutionResult? {
        val payment = paymentRepository.findByOrderId(orderId) ?: return null
        if (payment.status == PaymentStatus.SUCCEEDED && payment.paymentKey != null) {
            val gatewayResult = paymentGatewayRegistry.get(payment.paymentType).cancel(
                GatewayCancelCommand(
                    paymentKey = payment.paymentKey!!,
                    reason = reason,
                ),
            )
            payment.rawResponse = gatewayResult.rawResponse
        }
        payment.cancel(reason, LocalDateTime.now())
        return paymentRepository.saveAndFlush(payment).toResult()
    }

    override fun getPayment(orderId: Long): PaymentExecutionResult? =
        paymentRepository.findByOrderId(orderId)?.toResult()

    @Transactional
    override fun handleWebhook(provider: PaymentMethodType, request: PaymentWebhookRequest) {
        request.eventId?.let { eventId ->
            paymentWebhookRepository.findByProviderAndExternalEventId(provider.name, eventId)?.let { existing ->
                if (existing.processed) return
            }
        }

        val webhook = paymentWebhookRepository.save(
            PaymentWebhook(
                provider = provider.name,
                externalEventId = request.eventId,
                eventType = request.status.name,
                payload = objectMapper.writeValueAsString(request.payload),
            ),
        )

        val payment = findPayment(request) ?: run {
            webhook.markFailed(ErrorCode.PAYMENT_NOT_FOUND.message)
            throw PaymentException(ErrorCode.PAYMENT_NOT_FOUND)
        }

        webhook.paymentId = payment.id
        val now = LocalDateTime.now()
        when (request.status) {
            PaymentStatusView.SUCCEEDED -> {
                payment.markSucceeded(request.paymentId, now)
                eventPublisher.publishEvent(PaymentSucceededEvent(payment.orderId, payment.paymentKey ?: request.paymentId ?: payment.id.toString(), now))
            }
            PaymentStatusView.FAILED -> {
                val reason = request.reason ?: "PAYMENT_FAILED"
                payment.markFailed(reason, now)
                eventPublisher.publishEvent(PaymentFailedEvent(payment.orderId, request.paymentId, reason, now))
            }
            PaymentStatusView.CANCELED -> {
                val reason = request.reason ?: "PAYMENT_CANCELED"
                payment.cancel(reason, now)
                eventPublisher.publishEvent(PaymentCanceledEvent(payment.orderId, payment.paymentKey ?: request.paymentId ?: payment.id.toString(), reason, now))
            }
            PaymentStatusView.READY, PaymentStatusView.PENDING -> return
        }
        paymentRepository.saveAndFlush(payment)
        webhook.markProcessed(now)
    }

    private fun findPayment(request: PaymentWebhookRequest): Payment? =
        when {
            request.paymentId != null -> paymentRepository.findByPaymentKey(request.paymentId)
            request.orderId != null -> paymentRepository.findByOrderId(request.orderId)
            else -> null
        }
}

private fun Payment.toResult(redirectUrl: String? = null): PaymentExecutionResult =
    PaymentExecutionResult(
        paymentId = paymentKey ?: id.toString(),
        redirectUrl = redirectUrl,
        status = status.toView(),
        reason = failureMessage,
    )

private fun PaymentStatus.toView(): PaymentStatusView =
    when (this) {
        PaymentStatus.READY -> PaymentStatusView.READY
        PaymentStatus.PENDING -> PaymentStatusView.PENDING
        PaymentStatus.SUCCEEDED -> PaymentStatusView.SUCCEEDED
        PaymentStatus.FAILED -> PaymentStatusView.FAILED
        PaymentStatus.CANCELED -> PaymentStatusView.CANCELED
    }

private fun PaymentStatusView.toDomain(): PaymentStatus =
    when (this) {
        PaymentStatusView.READY -> PaymentStatus.READY
        PaymentStatusView.PENDING -> PaymentStatus.PENDING
        PaymentStatusView.SUCCEEDED -> PaymentStatus.SUCCEEDED
        PaymentStatusView.FAILED -> PaymentStatus.FAILED
        PaymentStatusView.CANCELED -> PaymentStatus.CANCELED
    }

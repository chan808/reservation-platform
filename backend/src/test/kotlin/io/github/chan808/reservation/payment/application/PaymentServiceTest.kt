package io.github.chan808.reservation.payment.application

import io.github.chan808.reservation.payment.api.PaymentCommand
import io.github.chan808.reservation.payment.api.PaymentConfirmCommand
import io.github.chan808.reservation.payment.api.PaymentExecutionResult
import io.github.chan808.reservation.payment.api.PaymentMethodType
import io.github.chan808.reservation.payment.api.PaymentStatusView
import io.github.chan808.reservation.payment.api.PaymentWebhookRequest
import io.github.chan808.reservation.payment.application.gateway.GatewayConfirmCommand
import io.github.chan808.reservation.payment.application.gateway.GatewayConfirmResult
import io.github.chan808.reservation.payment.application.gateway.GatewayPrepareResult
import io.github.chan808.reservation.payment.application.gateway.PaymentGateway
import io.github.chan808.reservation.payment.application.gateway.PaymentGatewayRegistry
import io.github.chan808.reservation.payment.domain.Payment
import io.github.chan808.reservation.payment.domain.PaymentStatus
import io.github.chan808.reservation.payment.infrastructure.persistence.PaymentRepository
import io.github.chan808.reservation.payment.infrastructure.persistence.PaymentWebhookRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import kotlin.test.assertEquals

class PaymentServiceTest {

    private val paymentRepository: PaymentRepository = mockk()
    private val paymentWebhookRepository: PaymentWebhookRepository = mockk()
    private val gatewayRegistry: PaymentGatewayRegistry = mockk()
    private val objectMapper = ObjectMapper()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val service = PaymentService(
        paymentRepository,
        paymentWebhookRepository,
        gatewayRegistry,
        objectMapper,
        eventPublisher,
    )

    @Test
    fun `preparePayment uses gateway result`() {
        val gateway = mockk<PaymentGateway>()
        val command = PaymentCommand(1L, "ORD-1", PaymentMethodType.TOSS, 10000, "buyer@example.com")
        every { gatewayRegistry.get(PaymentMethodType.TOSS) } returns gateway
        every { gateway.prepare(command) } returns GatewayPrepareResult(
            paymentId = "toss-1",
            redirectUrl = "https://pay.toss.local/checkout/toss-1",
            status = PaymentStatusView.READY,
            rawResponse = "{}",
        )
        every { paymentRepository.save(any()) } answers { firstArg<Payment>() }

        val result = service.preparePayment(command)

        assertEquals("toss-1", result.paymentId)
        assertEquals(PaymentStatusView.READY, result.status)
        assertEquals("https://pay.toss.local/checkout/toss-1", result.redirectUrl)
    }

    @Test
    fun `confirmPayment uses gateway confirm and updates payment`() {
        val gateway = mockk<PaymentGateway>()
        val payment = Payment(
            orderId = 1L,
            paymentType = PaymentMethodType.TOSS,
            paymentKey = "temp-key",
            providerOrderId = "ORD-1",
            status = PaymentStatus.READY,
            amount = 10000,
            requestedAt = LocalDateTime.now(),
            id = 1L,
        )
        every { paymentRepository.findByOrderId(1L) } returns payment
        every { gatewayRegistry.get(PaymentMethodType.TOSS) } returns gateway
        every {
            gateway.confirm(
                GatewayConfirmCommand(
                    paymentKey = "real-key",
                    orderNumber = "ORD-1",
                    amount = 10000,
                ),
            )
        } returns GatewayConfirmResult(
            paymentId = "real-key",
            status = PaymentStatusView.SUCCEEDED,
            rawResponse = """{"status":"DONE"}""",
        )
        every { eventPublisher.publishEvent(any<Any>()) } just Runs

        val result = service.confirmPayment(PaymentConfirmCommand(1L, "ORD-1", "real-key", 10000))

        assertEquals(PaymentStatusView.SUCCEEDED, result.status)
        assertEquals("real-key", payment.paymentKey)
        assertEquals(PaymentStatus.SUCCEEDED, payment.status)
    }

    @Test
    fun `handleWebhook success updates payment and publishes event`() {
        val payment = Payment(
            orderId = 1L,
            paymentType = PaymentMethodType.TOSS,
            paymentKey = "toss-1",
            providerOrderId = "ORD-1",
            status = PaymentStatus.READY,
            amount = 10000,
            requestedAt = LocalDateTime.now(),
            id = 1L,
        )
        every { paymentWebhookRepository.findByProviderAndExternalEventId("TOSS", "evt-1") } returns null
        every { paymentWebhookRepository.save(any()) } answers { firstArg() }
        every { paymentRepository.findByPaymentKey("toss-1") } returns payment
        every { eventPublisher.publishEvent(any<Any>()) } just Runs

        service.handleWebhook(
            PaymentMethodType.TOSS,
            PaymentWebhookRequest(
                eventId = "evt-1",
                paymentId = "toss-1",
                orderId = null,
                status = PaymentStatusView.SUCCEEDED,
                payload = mapOf("status" to "DONE"),
            ),
        )

        assertEquals(PaymentStatus.SUCCEEDED, payment.status)
        verify { eventPublisher.publishEvent(any<Any>()) }
    }
}

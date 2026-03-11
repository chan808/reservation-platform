package io.github.chan808.reservation.order.application

import io.github.chan808.reservation.common.ErrorCode
import io.github.chan808.reservation.common.OrderException
import io.github.chan808.reservation.member.api.AuthMemberView
import io.github.chan808.reservation.member.api.MemberApi
import io.github.chan808.reservation.order.domain.Order
import io.github.chan808.reservation.order.domain.OrderStatus
import io.github.chan808.reservation.order.infrastructure.persistence.OrderRepository
import io.github.chan808.reservation.order.infrastructure.persistence.OrderStatusHistoryRepository
import io.github.chan808.reservation.order.presentation.CancelOrderRequest
import io.github.chan808.reservation.order.presentation.ConfirmOrderPaymentRequest
import io.github.chan808.reservation.order.presentation.CreateOrderRequest
import io.github.chan808.reservation.payment.api.PaymentApi
import io.github.chan808.reservation.payment.api.PaymentExecutionResult
import io.github.chan808.reservation.payment.api.PaymentMethodType
import io.github.chan808.reservation.payment.api.PaymentStatusView
import io.github.chan808.reservation.product.api.ProductApi
import io.github.chan808.reservation.product.api.StockReservationResult
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import kotlin.test.assertEquals

class OrderServiceTest {

    private val orderRepository: OrderRepository = mockk()
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository = mockk()
    private val memberApi: MemberApi = mockk()
    private val productApi: ProductApi = mockk()
    private val paymentApi: PaymentApi = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val orderService = OrderService(
        orderRepository,
        orderStatusHistoryRepository,
        memberApi,
        productApi,
        paymentApi,
        eventPublisher,
    )

    private val memberView = AuthMemberView(
        id = 1L,
        email = "buyer@example.com",
        encodedPassword = "encoded",
        role = "USER",
        emailVerified = true,
        provider = null,
    )

    @Test
    fun `create reserves stock and prepares payment`() {
        val request = CreateOrderRequest(
            productId = 10L,
            quantity = 2,
            orderRequestId = "req-1",
            paymentType = PaymentMethodType.TOSS,
        )
        every { memberApi.findAuthMemberById(1L) } returns memberView
        every { orderRepository.findByOrderRequestId("req-1") } returns null
        every { orderRepository.existsByMemberIdAndProductIdAndStatusIn(1L, 10L, any()) } returns false
        every { productApi.reserveStock(any()) } returns StockReservationResult(10L, 2, 5000, 8)
        every { orderRepository.save(any()) } answers { firstArg<Order>() }
        every { orderStatusHistoryRepository.save(any()) } answers { firstArg() }
        every { paymentApi.preparePayment(any()) } returns PaymentExecutionResult(
            paymentId = "pay-1",
            redirectUrl = null,
            status = PaymentStatusView.READY,
        )
        every { eventPublisher.publishEvent(any<Any>()) } just Runs

        val response = orderService.create(1L, request)

        assertEquals(10000, response.totalPrice)
        assertEquals(OrderStatus.PENDING_PAYMENT, response.status)
        assertEquals(PaymentStatusView.READY, response.paymentStatus)
        verify { productApi.reserveStock(any()) }
        verify { paymentApi.preparePayment(any()) }
    }

    @Test
    fun `duplicate active order is blocked`() {
        val request = CreateOrderRequest(
            productId = 10L,
            quantity = 1,
            orderRequestId = "req-2",
            paymentType = PaymentMethodType.TOSS,
        )
        every { memberApi.findAuthMemberById(1L) } returns memberView
        every { orderRepository.findByOrderRequestId("req-2") } returns null
        every { orderRepository.existsByMemberIdAndProductIdAndStatusIn(1L, 10L, any()) } returns true

        val ex = assertThrows<OrderException> { orderService.create(1L, request) }

        assertEquals(ErrorCode.DUPLICATE_ACTIVE_ORDER, ex.errorCode)
    }

    @Test
    fun `cancel releases stock and cancels payment`() {
        val order = Order(
            orderNumber = "ORD-1",
            memberId = 1L,
            productId = 10L,
            orderRequestId = "req-1",
            quantity = 2,
            unitPrice = 5000,
            totalPrice = 10000,
            status = OrderStatus.PENDING_PAYMENT,
            paymentType = PaymentMethodType.TOSS,
            paymentDeadlineAt = LocalDateTime.now().plusMinutes(15),
            orderedAt = LocalDateTime.now(),
            id = 1L,
        )
        every { orderRepository.findByIdAndMemberId(1L, 1L) } returns order
        every { paymentApi.cancelPayment(1L, any()) } returns PaymentExecutionResult(
            paymentId = "pay-1",
            redirectUrl = null,
            status = PaymentStatusView.CANCELED,
        )
        every { productApi.releaseStock(10L, 2) } just Runs
        every { orderStatusHistoryRepository.save(any()) } answers { firstArg() }
        every { paymentApi.getPayment(1L) } returns PaymentExecutionResult(
            paymentId = "pay-1",
            redirectUrl = null,
            status = PaymentStatusView.CANCELED,
        )

        val response = orderService.cancel(1L, 1L, CancelOrderRequest("changed mind"))

        assertEquals(OrderStatus.CANCELED, response.status)
        verify { productApi.releaseStock(10L, 2) }
        verify { paymentApi.cancelPayment(1L, any()) }
    }

    @Test
    fun `expireTimedOutOrders releases stock and cancels payment`() {
        val timedOut = Order(
            orderNumber = "ORD-2",
            memberId = 1L,
            productId = 10L,
            orderRequestId = "req-2",
            quantity = 1,
            unitPrice = 5000,
            totalPrice = 5000,
            status = OrderStatus.PENDING_PAYMENT,
            paymentType = PaymentMethodType.TOSS,
            paymentDeadlineAt = LocalDateTime.now().minusMinutes(1),
            orderedAt = LocalDateTime.now().minusMinutes(20),
            id = 2L,
        )
        every { orderRepository.findAllByStatusAndPaymentDeadlineAtBefore(OrderStatus.PENDING_PAYMENT, any()) } returns listOf(timedOut)
        every { paymentApi.cancelPayment(2L, "ORDER_EXPIRED") } returns PaymentExecutionResult(
            paymentId = "pay-2",
            redirectUrl = null,
            status = PaymentStatusView.CANCELED,
        )
        every { productApi.releaseStock(10L, 1) } just Runs
        every { orderStatusHistoryRepository.save(any()) } answers { firstArg() }

        val count = orderService.expireTimedOutOrders(LocalDateTime.now())

        assertEquals(1, count)
        assertEquals(OrderStatus.EXPIRED, timedOut.status)
        verify { productApi.releaseStock(10L, 1) }
    }

    @Test
    fun `confirmPayment updates order synchronously when payment succeeds`() {
        val order = Order(
            orderNumber = "ORD-3",
            memberId = 1L,
            productId = 10L,
            orderRequestId = "req-3",
            quantity = 1,
            unitPrice = 10000,
            totalPrice = 10000,
            status = OrderStatus.PENDING_PAYMENT,
            paymentType = PaymentMethodType.PAYPAL,
            paymentDeadlineAt = LocalDateTime.now().plusMinutes(15),
            orderedAt = LocalDateTime.now(),
            id = 3L,
        )
        every { orderRepository.findByIdAndMemberId(3L, 1L) } returns order
        every { paymentApi.confirmPayment(any()) } returns PaymentExecutionResult(
            paymentId = "pay-3",
            redirectUrl = null,
            status = PaymentStatusView.SUCCEEDED,
        )
        every { orderStatusHistoryRepository.save(any()) } answers { firstArg() }

        val result = orderService.confirmPayment(1L, 3L, ConfirmOrderPaymentRequest("pay-3", 10000))

        assertEquals(PaymentStatusView.SUCCEEDED, result.status)
        assertEquals(OrderStatus.PAID, order.status)
        verify(exactly = 0) { productApi.releaseStock(any(), any()) }
        verify { orderStatusHistoryRepository.save(any()) }
    }

    @Test
    fun `confirmPayment releases stock synchronously when payment fails`() {
        val order = Order(
            orderNumber = "ORD-4",
            memberId = 1L,
            productId = 10L,
            orderRequestId = "req-4",
            quantity = 1,
            unitPrice = 10000,
            totalPrice = 10000,
            status = OrderStatus.PENDING_PAYMENT,
            paymentType = PaymentMethodType.PAYPAL,
            paymentDeadlineAt = LocalDateTime.now().plusMinutes(15),
            orderedAt = LocalDateTime.now(),
            id = 4L,
        )
        every { orderRepository.findByIdAndMemberId(4L, 1L) } returns order
        every { paymentApi.confirmPayment(any()) } returns PaymentExecutionResult(
            paymentId = "pay-4",
            redirectUrl = null,
            status = PaymentStatusView.FAILED,
            reason = "PAYMENT_CONFIRM_FAILED",
        )
        every { productApi.releaseStock(10L, 1) } just Runs
        every { orderStatusHistoryRepository.save(any()) } answers { firstArg() }

        val result = orderService.confirmPayment(1L, 4L, ConfirmOrderPaymentRequest("pay-4", 10000))

        assertEquals(PaymentStatusView.FAILED, result.status)
        assertEquals(OrderStatus.PAYMENT_FAILED, order.status)
        verify { productApi.releaseStock(10L, 1) }
        verify { orderStatusHistoryRepository.save(any()) }
    }
}

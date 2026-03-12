package io.github.chan808.reservation.order.application

import io.github.chan808.reservation.inventory.api.InventoryApi
import io.github.chan808.reservation.order.domain.Order
import io.github.chan808.reservation.order.domain.OrderStatus
import io.github.chan808.reservation.order.domain.OrderStatusHistory
import io.github.chan808.reservation.order.infrastructure.persistence.OrderRepository
import io.github.chan808.reservation.order.infrastructure.persistence.OrderStatusHistoryRepository
import io.github.chan808.reservation.order.infrastructure.persistence.ProcessedKafkaMessage
import io.github.chan808.reservation.order.infrastructure.persistence.ProcessedKafkaMessageRepository
import io.github.chan808.reservation.payment.api.PaymentMethodType
import io.github.chan808.reservation.payment.events.PaymentResultEvent
import io.github.chan808.reservation.payment.events.PaymentResultEventType
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDateTime
import kotlin.test.assertEquals

class PaymentResultEventProcessorTest {

    private val orderRepository: OrderRepository = mockk()
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository = mockk()
    private val processedKafkaMessageRepository: ProcessedKafkaMessageRepository = mockk()
    private val inventoryApi: InventoryApi = mockk()
    private val processor = PaymentResultEventProcessor(
        orderRepository,
        orderStatusHistoryRepository,
        processedKafkaMessageRepository,
        inventoryApi,
        "order-payment-result",
    )

    @Test
    fun `success event marks order as paid`() {
        val order = paymentProcessingOrder(1L)
        val historySlot = slot<OrderStatusHistory>()
        every { processedKafkaMessageRepository.save(any()) } answers { firstArg<ProcessedKafkaMessage>() }
        every { orderRepository.findByIdForUpdate(1L) } returns order
        every { orderStatusHistoryRepository.save(capture(historySlot)) } answers { firstArg() }

        processor.process(successEvent(order.id))

        assertEquals(OrderStatus.PAID, order.status)
        assertEquals(OrderStatus.PAYMENT_PROCESSING.name, historySlot.captured.fromStatus)
        assertEquals(OrderStatus.PAID.name, historySlot.captured.toStatus)
        verify(exactly = 0) { inventoryApi.releaseStock(any(), any()) }
    }

    @Test
    fun `failed event marks order as payment failed and releases stock`() {
        val order = paymentProcessingOrder(2L)
        val historySlot = slot<OrderStatusHistory>()
        every { processedKafkaMessageRepository.save(any()) } answers { firstArg<ProcessedKafkaMessage>() }
        every { orderRepository.findByIdForUpdate(2L) } returns order
        every { inventoryApi.releaseStock(order.productId, order.quantity) } just runs
        every { orderStatusHistoryRepository.save(capture(historySlot)) } answers { firstArg() }

        processor.process(
            PaymentResultEvent(
                eventId = "evt-2",
                eventType = PaymentResultEventType.FAILED,
                orderId = order.id,
                paymentId = "pay-2",
                reason = "PAYMENT_CONFIRM_FAILED",
                occurredAt = LocalDateTime.now(),
            ),
        )

        assertEquals(OrderStatus.PAYMENT_FAILED, order.status)
        assertEquals(OrderStatus.PAYMENT_PROCESSING.name, historySlot.captured.fromStatus)
        assertEquals(OrderStatus.PAYMENT_FAILED.name, historySlot.captured.toStatus)
        verify { inventoryApi.releaseStock(order.productId, order.quantity) }
    }

    @Test
    fun `canceled event marks order as canceled and releases stock`() {
        val order = paymentProcessingOrder(3L)
        val historySlot = slot<OrderStatusHistory>()
        every { processedKafkaMessageRepository.save(any()) } answers { firstArg<ProcessedKafkaMessage>() }
        every { orderRepository.findByIdForUpdate(3L) } returns order
        every { inventoryApi.releaseStock(order.productId, order.quantity) } just runs
        every { orderStatusHistoryRepository.save(capture(historySlot)) } answers { firstArg() }

        processor.process(
            PaymentResultEvent(
                eventId = "evt-3",
                eventType = PaymentResultEventType.CANCELED,
                orderId = order.id,
                paymentId = "pay-3",
                reason = "PAYMENT_CANCELED",
                occurredAt = LocalDateTime.now(),
            ),
        )

        assertEquals(OrderStatus.CANCELED, order.status)
        assertEquals(OrderStatus.PAYMENT_PROCESSING.name, historySlot.captured.fromStatus)
        assertEquals(OrderStatus.CANCELED.name, historySlot.captured.toStatus)
        verify { inventoryApi.releaseStock(order.productId, order.quantity) }
    }

    @Test
    fun `duplicate event is ignored`() {
        every { processedKafkaMessageRepository.save(any()) } throws DataIntegrityViolationException("duplicate")

        processor.process(successEvent(4L))

        verify(exactly = 0) { orderRepository.findByIdForUpdate(any()) }
        verify(exactly = 0) { orderStatusHistoryRepository.save(any()) }
        verify(exactly = 0) { inventoryApi.releaseStock(any(), any()) }
    }

    private fun successEvent(orderId: Long) = PaymentResultEvent(
        eventId = "evt-$orderId",
        eventType = PaymentResultEventType.SUCCEEDED,
        orderId = orderId,
        paymentId = "pay-$orderId",
        reason = null,
        occurredAt = LocalDateTime.now(),
    )

    private fun paymentProcessingOrder(orderId: Long) = Order(
        orderNumber = "ORD-$orderId",
        memberId = 1L,
        productId = 10L,
        orderRequestId = "req-$orderId",
        quantity = 1,
        unitPrice = 10000,
        totalPrice = 10000,
        status = OrderStatus.PAYMENT_PROCESSING,
        paymentType = PaymentMethodType.PAYPAL,
        paymentDeadlineAt = LocalDateTime.now().plusMinutes(15),
        orderedAt = LocalDateTime.now(),
        id = orderId,
    )
}

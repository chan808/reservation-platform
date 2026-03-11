package io.github.chan808.reservation.order.presentation

import com.ninjasquad.springmockk.MockkBean
import io.github.chan808.reservation.auth.infrastructure.security.JwtProvider
import io.github.chan808.reservation.auth.infrastructure.security.SecurityConfig
import io.github.chan808.reservation.auth.infrastructure.security.SecurityExceptionHandler
import io.github.chan808.reservation.common.ClientIpResolver
import io.github.chan808.reservation.order.application.OrderService
import io.github.chan808.reservation.order.domain.OrderStatus
import io.github.chan808.reservation.payment.api.PaymentExecutionResult
import io.github.chan808.reservation.payment.api.PaymentMethodType
import io.github.chan808.reservation.payment.api.PaymentStatusView
import io.jsonwebtoken.Claims
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

@WebMvcTest(OrderController::class)
@Import(SecurityConfig::class, SecurityExceptionHandler::class)
@TestPropertySource(
    properties = [
        "jwt.refresh-token-expiry=604800",
        "cookie.secure=false",
        "cors.allowed-origin=http://localhost:3000",
    ],
)
class OrderControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean lateinit var orderService: OrderService
    @MockkBean lateinit var jwtProvider: JwtProvider
    @MockkBean lateinit var clientIpResolver: ClientIpResolver

    private val response = OrderResponse(
        id = 1L,
        orderNumber = "ORD-1",
        productId = 10L,
        quantity = 2,
        unitPrice = 5000,
        totalPrice = 10000,
        status = OrderStatus.PENDING_PAYMENT,
        paymentType = PaymentMethodType.TOSS,
        paymentStatus = PaymentStatusView.READY,
        paymentId = "pay-1",
        paymentDeadlineAt = LocalDateTime.now().plusMinutes(15),
        orderedAt = LocalDateTime.now(),
        canceledAt = null,
        cancelReason = null,
    )

    @BeforeEach
    fun setup() {
        every { clientIpResolver.resolve(any()) } returns "127.0.0.1"
        val claims = mockk<Claims>()
        every { claims.subject } returns "1"
        every { claims["role"] } returns "USER"
        every { jwtProvider.validate("test-token") } returns claims
    }

    @Test
    fun `create requires authentication`() {
        mockMvc.post("/api/orders") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"productId":10,"quantity":1,"orderRequestId":"req-1","paymentType":"TOSS"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `create returns 201`() {
        every { orderService.create(1L, any()) } returns response

        mockMvc.post("/api/orders") {
            header("Authorization", "Bearer test-token")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"productId":10,"quantity":1,"orderRequestId":"req-1","paymentType":"TOSS"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.orderNumber") { value("ORD-1") }
        }
    }

    @Test
    fun `get returns 200`() {
        every { orderService.get(1L, 1L) } returns response

        mockMvc.get("/api/orders/1") {
            header("Authorization", "Bearer test-token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalPrice") { value(10000) }
        }
    }

    @Test
    fun `confirm payment returns 200`() {
        every { orderService.confirmPayment(1L, 1L, any()) } returns PaymentExecutionResult(
            paymentId = "real-key",
            redirectUrl = null,
            status = PaymentStatusView.SUCCEEDED,
        )

        mockMvc.post("/api/orders/1/confirm-payment") {
            header("Authorization", "Bearer test-token")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"paymentKey":"real-key","amount":10000}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("SUCCEEDED") }
        }
    }
}

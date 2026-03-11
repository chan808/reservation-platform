package io.github.chan808.reservation.product.presentation

import com.ninjasquad.springmockk.MockkBean
import io.github.chan808.reservation.auth.infrastructure.security.JwtProvider
import io.github.chan808.reservation.auth.infrastructure.security.SecurityConfig
import io.github.chan808.reservation.auth.infrastructure.security.SecurityExceptionHandler
import io.github.chan808.reservation.common.ClientIpResolver
import io.github.chan808.reservation.product.application.ProductService
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

@WebMvcTest(ProductController::class)
@Import(SecurityConfig::class, SecurityExceptionHandler::class)
@TestPropertySource(
    properties = [
        "jwt.refresh-token-expiry=604800",
        "cookie.secure=false",
        "cors.allowed-origin=http://localhost:3000",
    ],
)
class ProductControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean lateinit var productService: ProductService
    @MockkBean lateinit var jwtProvider: JwtProvider
    @MockkBean lateinit var clientIpResolver: ClientIpResolver

    @BeforeEach
    fun setup() {
        every { clientIpResolver.resolve(any()) } returns "127.0.0.1"
        val userClaims = mockk<Claims>()
        every { userClaims.subject } returns "1"
        every { userClaims["role"] } returns "USER"
        every { jwtProvider.validate("user-token") } returns userClaims

        val adminClaims = mockk<Claims>()
        every { adminClaims.subject } returns "2"
        every { adminClaims["role"] } returns "ADMIN"
        every { jwtProvider.validate("admin-token") } returns adminClaims
    }

    @Test
    fun `list is public`() {
        every { productService.list(0, 20) } returns io.github.chan808.reservation.common.PageResponse(
            content = emptyList(),
            page = 0,
            size = 20,
            totalElements = 0,
            totalPages = 0,
            first = true,
            last = true,
        )

        mockMvc.get("/api/products")
            .andExpect { status { isOk() } }
    }

    @Test
    fun `create requires admin authority`() {
        mockMvc.post("/api/products") {
            header("Authorization", "Bearer user-token")
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """
                {
                  "name":"Limited",
                  "description":"desc",
                  "price":1000,
                  "stockQuantity":10,
                  "saleStartAt":"${LocalDateTime.now().plusHours(1)}"
                }
            """.trimIndent()
        }.andExpect {
            status { isForbidden() }
        }
    }
}

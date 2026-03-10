package io.github.chan808.authtemplate.common.exception

import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.MemberException
import io.github.chan808.authtemplate.common.config.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `비즈니스 예외는 ErrorCode에 정의된 HTTP 상태코드와 메시지로 응답한다`() {
        val request = mockk<HttpServletRequest> { every { requestURI } returns "/api/members/me" }

        val response = handler.handleBusinessException(MemberException(ErrorCode.MEMBER_NOT_FOUND), request)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MEMBER_NOT_FOUND.message, response.body?.detail)
        assertEquals(ErrorCode.MEMBER_NOT_FOUND.name, response.body?.title)
    }

    @Test
    fun `커스텀 메시지를 가진 예외는 재정의된 메시지로 응답한다`() {
        val request = mockk<HttpServletRequest> { every { requestURI } returns "/api/auth/login" }

        val response = handler.handleBusinessException(
            MemberException(ErrorCode.MEMBER_NOT_FOUND, "탈퇴한 회원입니다."),
            request,
        )

        assertEquals("탈퇴한 회원입니다.", response.body?.detail)
    }

    @Test
    fun `미처리 예외는 원본 메시지를 노출하지 않고 500으로 응답한다`() {
        val request = mockk<HttpServletRequest> { every { requestURI } returns "/api/test" }

        val response = handler.handleUnexpected(RuntimeException("DB password: supersecret"), request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.message, response.body?.detail)
        assertFalse(response.body?.detail?.contains("supersecret") ?: false)
    }
}

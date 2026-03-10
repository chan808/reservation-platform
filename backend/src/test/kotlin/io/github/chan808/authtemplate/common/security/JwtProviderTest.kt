package io.github.chan808.authtemplate.common.security

import io.github.chan808.authtemplate.auth.infrastructure.security.JwtProperties
import io.github.chan808.authtemplate.auth.infrastructure.security.JwtProvider
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class JwtProviderTest {

    private val props = JwtProperties(
        secret = "test-secret-key-must-be-at-least-32-bytes-long-for-hs256",
        accessTokenExpiry = 1800,
        refreshTokenExpiry = 604800,
    )
    private val jwtProvider = JwtProvider(props)

    @Test
    fun `AT 생성 후 검증하면 memberId와 role을 정확히 추출한다`() {
        val token = jwtProvider.generateAccessToken(1L, "USER")
        val claims = jwtProvider.validate(token)

        assertEquals("1", claims.subject)
        assertEquals("USER", claims["role"])
    }

    @Test
    fun `변조된 토큰 검증 시 JwtException이 발생한다`() {
        val token = jwtProvider.generateAccessToken(1L, "USER")
        assertThrows<JwtException> { jwtProvider.validate(token.dropLast(10) + "TAMPERED!!") }
    }

    @Test
    fun `만료된 토큰 검증 시 ExpiredJwtException이 발생한다`() {
        val expiredProvider = JwtProvider(props.copy(accessTokenExpiry = -1))
        val token = expiredProvider.generateAccessToken(1L, "USER")
        assertThrows<ExpiredJwtException> { jwtProvider.validate(token) }
    }
}

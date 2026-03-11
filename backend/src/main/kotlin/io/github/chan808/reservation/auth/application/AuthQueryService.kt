package io.github.chan808.reservation.auth.application

import io.github.chan808.reservation.auth.application.port.AccessTokenPort
import io.jsonwebtoken.JwtException
import org.springframework.stereotype.Service

@Service
class AuthQueryService(
    private val accessTokenPort: AccessTokenPort,
) {

    fun validateToken(token: String): Long? =
        try {
            accessTokenPort.getMemberId(token)
        } catch (_: JwtException) {
            null
        }
}

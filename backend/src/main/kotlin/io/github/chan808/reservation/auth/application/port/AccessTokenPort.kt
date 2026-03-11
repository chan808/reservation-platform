package io.github.chan808.reservation.auth.application.port

interface AccessTokenPort {
    fun generateAccessToken(memberId: Long, role: String): String
    fun getMemberId(token: String): Long
}

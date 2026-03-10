package io.github.chan808.authtemplate.auth.application.port

interface AccessTokenPort {
    fun generateAccessToken(memberId: Long, role: String): String
    fun getMemberId(token: String): Long
}

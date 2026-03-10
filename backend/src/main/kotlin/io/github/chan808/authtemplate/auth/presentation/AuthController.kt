package io.github.chan808.authtemplate.auth.presentation

import io.github.chan808.authtemplate.auth.application.AuthCommandService
import io.github.chan808.authtemplate.auth.application.PasswordResetService
import io.github.chan808.authtemplate.auth.infrastructure.redis.OAuthCodeStore
import io.github.chan808.authtemplate.common.ApiResponse
import io.github.chan808.authtemplate.common.AuthException
import io.github.chan808.authtemplate.common.ClientIpResolver
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.member.api.MemberApi
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authCommandService: AuthCommandService,
    private val memberApi: MemberApi,
    private val passwordResetService: PasswordResetService,
    private val clientIpResolver: ClientIpResolver,
    private val oAuthCodeStore: OAuthCodeStore,
    @Value("\${cookie.secure:false}") private val cookieSecure: Boolean,
    @Value("\${jwt.refresh-token-expiry}") private val rtTtl: Long,
) {

    @PostMapping("/login")
    fun login(
        @RequestBody @Valid request: LoginRequest,
        servletRequest: HttpServletRequest,
        response: jakarta.servlet.http.HttpServletResponse,
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val (at, rt) = authCommandService.login(request, clientIpResolver.resolve(servletRequest))
        response.addHeader(HttpHeaders.SET_COOKIE, buildRtCookie(rt, rtTtl).toString())
        return ResponseEntity.ok(ApiResponse.of(TokenResponse(at)))
    }

    @PostMapping("/reissue")
    fun reissue(
        @CookieValue("refresh_token", required = false) rtToken: String?,
        @RequestHeader("X-CSRF-GUARD") csrfGuard: String,
        response: jakarta.servlet.http.HttpServletResponse,
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val refreshToken = rtToken ?: throw AuthException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)
        val (at, newRt) = authCommandService.reissue(refreshToken)
        response.addHeader(HttpHeaders.SET_COOKIE, buildRtCookie(newRt, rtTtl).toString())
        return ResponseEntity.ok(ApiResponse.of(TokenResponse(at)))
    }

    @PostMapping("/logout")
    fun logout(
        @CookieValue("refresh_token", required = false) rtToken: String?,
        @RequestHeader("X-CSRF-GUARD") csrfGuard: String,
        response: jakarta.servlet.http.HttpServletResponse,
    ): ResponseEntity<ApiResponse<Unit>> {
        authCommandService.logout(rtToken)
        response.addHeader(HttpHeaders.SET_COOKIE, buildRtCookie("", 0).toString())
        return ResponseEntity.ok(ApiResponse.success())
    }

    @GetMapping("/verify-email")
    fun verifyEmail(@RequestParam token: String): ResponseEntity<ApiResponse<Unit>> {
        memberApi.verifyEmail(token)
        return ResponseEntity.ok(ApiResponse.success())
    }

    @PostMapping("/verify-email/resend")
    fun resendVerificationEmail(
        @RequestBody @Valid request: EmailVerificationResendRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        memberApi.resendVerification(request.email, clientIpResolver.resolve(servletRequest))
        return ResponseEntity.ok(ApiResponse.success("If the account exists and is not verified, a new verification email has been sent."))
    }

    @PostMapping("/password-reset/request")
    fun requestPasswordReset(
        @RequestBody @Valid request: PasswordResetRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        passwordResetService.requestReset(request.email, clientIpResolver.resolve(servletRequest))
        return ResponseEntity.ok(ApiResponse.success())
    }

    @GetMapping("/oauth2/token")
    fun exchangeOAuthCode(@RequestParam code: String): ResponseEntity<ApiResponse<TokenResponse>> {
        val accessToken = oAuthCodeStore.findAndDelete(code)
            ?: throw AuthException(ErrorCode.OAUTH_CODE_NOT_FOUND)
        return ResponseEntity.ok(ApiResponse.of(TokenResponse(accessToken)))
    }

    @PostMapping("/password-reset/confirm")
    fun confirmPasswordReset(
        @RequestBody @Valid request: PasswordResetConfirmRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        passwordResetService.confirmReset(request.token, request.newPassword)
        return ResponseEntity.ok(ApiResponse.success())
    }

    private fun buildRtCookie(value: String, maxAge: Long): ResponseCookie =
        ResponseCookie.from(RT_COOKIE_NAME, value)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(maxAge)
            .build()

    companion object {
        private const val RT_COOKIE_NAME = "refresh_token"
    }
}

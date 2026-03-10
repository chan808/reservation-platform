package io.github.chan808.authtemplate.auth.infrastructure.oauth2

import io.github.chan808.authtemplate.auth.application.AuthCommandService
import io.github.chan808.authtemplate.auth.infrastructure.redis.OAuthCodeStore
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class OAuth2SuccessHandler(
    private val authService: AuthCommandService,
    private val oAuthCodeStore: OAuthCodeStore,
    @Value("\${app.base-url}") private val frontendBaseUrl: String,
    @Value("\${app.default-locale:ko}") private val defaultLocale: String,
    @Value("\${cookie.secure:false}") private val cookieSecure: Boolean,
    @Value("\${jwt.refresh-token-expiry}") private val rtExpiry: Long,
) : AuthenticationSuccessHandler {

    private val log = LoggerFactory.getLogger(OAuth2SuccessHandler::class.java)

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oAuth2User = authentication.principal as AuthenticatedOAuth2User
        val (accessToken, rawRt) = authService.issueTokensForOAuth(oAuth2User.memberId)

        response.addCookie(Cookie("refresh_token", rawRt).apply {
            isHttpOnly = true
            secure = cookieSecure
            path = "/api/auth"
            maxAge = rtExpiry.toInt()
            setAttribute("SameSite", "Strict")
        })

        val code = UUID.randomUUID().toString()
        oAuthCodeStore.save(code, accessToken)

        val locale = resolveLocale(request)
        log.info("[AUTH] OAuth2 login success memberId={} provider={} locale={}", oAuth2User.memberId, oAuth2User.provider, locale)
        response.sendRedirect("$frontendBaseUrl/$locale/auth/callback?code=$code")
    }

    private fun resolveLocale(request: HttpServletRequest): String {
        val session = request.getSession(false) ?: return defaultLocale
        val locale = session.getAttribute(LocaleAwareOAuth2AuthorizationRequestResolver.SESSION_KEY) as? String
        session.removeAttribute(LocaleAwareOAuth2AuthorizationRequestResolver.SESSION_KEY)
        return locale ?: defaultLocale
    }
}

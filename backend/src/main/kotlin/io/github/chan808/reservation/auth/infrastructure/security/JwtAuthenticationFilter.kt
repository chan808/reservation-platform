package io.github.chan808.reservation.auth.infrastructure.security

import io.github.chan808.reservation.auth.application.port.TokenStore
import io.github.chan808.reservation.common.ErrorCode
import io.github.chan808.reservation.member.api.MemberApi
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

// @Component 미사용: SecurityFilterChain에만 등록하기 위해 SecurityConfig에서 직접 인스턴스화
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
    private val memberApi: MemberApi,
    private val tokenStore: TokenStore,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        resolveToken(request)?.let { token ->
            try {
                val claims = jwtProvider.validate(token)
                val memberId = claims.subject.toLong()
                val tokenVersion = (claims["tokenVersion"] as? Number)?.toLong() ?: throw JwtException("Missing tokenVersion claim")
                val currentTokenVersion = tokenStore.findAccessTokenVersion(memberId)
                    ?: memberApi.findAuthMemberById(memberId)?.also {
                        tokenStore.cacheAccessTokenVersion(memberId, it.tokenVersion)
                    }?.tokenVersion

                if (currentTokenVersion == null || currentTokenVersion != tokenVersion) {
                    SecurityContextHolder.clearContext()
                    log.info(
                        "[AUTH] rejected invalidated access token memberId={} tokenVersion={} currentTokenVersion={}",
                        memberId,
                        tokenVersion,
                        currentTokenVersion,
                    )
                    request.setAttribute("jwt-error", ErrorCode.TOKEN_INVALID)
                    chain.doFilter(request, response)
                    return
                }

                val auth = UsernamePasswordAuthenticationToken(
                    memberId,
                    null,
                    listOf(SimpleGrantedAuthority(claims["role"] as String)),
                )
                SecurityContextHolder.getContext().authentication = auth
            } catch (ex: ExpiredJwtException) {
                SecurityContextHolder.clearContext()
                // 만료/위조 구분: EntryPoint에서 세분화된 401 응답을 위해 속성으로 전달
                request.setAttribute("jwt-error", ErrorCode.TOKEN_EXPIRED)
            } catch (ex: JwtException) {
                SecurityContextHolder.clearContext()
                request.setAttribute("jwt-error", ErrorCode.TOKEN_INVALID)
            }
        }
        chain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? =
        request.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ") }
            ?.substring(7)
}

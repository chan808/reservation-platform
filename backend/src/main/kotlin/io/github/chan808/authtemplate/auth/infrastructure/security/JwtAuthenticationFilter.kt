package io.github.chan808.authtemplate.auth.infrastructure.security

import io.github.chan808.authtemplate.common.ErrorCode
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

// @Component 미사용: SecurityFilterChain에만 등록하기 위해 SecurityConfig에서 직접 인스턴스화
class JwtAuthenticationFilter(private val jwtProvider: JwtProvider) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        resolveToken(request)?.let { token ->
            try {
                val claims = jwtProvider.validate(token)
                val auth = UsernamePasswordAuthenticationToken(
                    claims.subject.toLong(),
                    null,
                    listOf(SimpleGrantedAuthority(claims["role"] as String)),
                )
                SecurityContextHolder.getContext().authentication = auth
            } catch (ex: ExpiredJwtException) {
                // 만료/위조 구분: EntryPoint에서 세분화된 401 응답을 위해 속성으로 전달
                request.setAttribute("jwt-error", ErrorCode.TOKEN_EXPIRED)
            } catch (ex: JwtException) {
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

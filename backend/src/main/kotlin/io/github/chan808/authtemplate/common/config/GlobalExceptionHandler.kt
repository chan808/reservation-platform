package io.github.chan808.authtemplate.common.config

import io.github.chan808.authtemplate.common.BusinessException
import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.RateLimitException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestCookieException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

// 필터 레벨(Spring Security) 예외는 잡히지 않음 → AuthenticationEntryPoint / AccessDeniedHandler에서 별도 처리
@RestControllerAdvice
class GlobalExceptionHandler {

    // RateLimitException을 BusinessException보다 먼저 등록해 Retry-After 헤더가 누락되지 않도록 함
    @ExceptionHandler(RateLimitException::class)
    fun handleRateLimit(ex: RateLimitException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val detail = buildProblemDetail(ex.errorCode, ex.message, request.requestURI)
        return ResponseEntity.status(ex.errorCode.httpStatus)
            .header("Retry-After", ex.retryAfterSeconds.toString())
            .body(detail)
    }

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val detail = buildProblemDetail(ex.errorCode, ex.message, request.requestURI)
        return ResponseEntity.status(ex.errorCode.httpStatus).body(detail)
    }

    // 필드별 검증 오류를 errors 프로퍼티로 구조화해 클라이언트 폼 처리 용이하게 함
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val fieldErrors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "유효하지 않은 값") }
        val detail = buildProblemDetail(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.message, request.requestURI)
        detail.setProperty("errors", fieldErrors)
        return ResponseEntity.badRequest().body(detail)
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val detail = buildProblemDetail(ErrorCode.INVALID_INPUT, "필수 헤더 누락: ${ex.headerName}", request.requestURI)
        return ResponseEntity.badRequest().body(detail)
    }

    // RT 쿠키 없이 /reissue, /logout 호출 시 — 기본 처리 시 500으로 빠지므로 401로 명시
    @ExceptionHandler(MissingRequestCookieException::class)
    fun handleMissingCookie(ex: MissingRequestCookieException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val detail = buildProblemDetail(ErrorCode.REFRESH_TOKEN_NOT_FOUND, ErrorCode.REFRESH_TOKEN_NOT_FOUND.message, request.requestURI)
        return ResponseEntity.status(ErrorCode.REFRESH_TOKEN_NOT_FOUND.httpStatus).body(detail)
    }

    // 미처리 예외: 원본 메시지 노출 차단, 서버 로그에만 기록
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        log.error("Unhandled exception at {}: {}", request.requestURI, ex.message, ex)
        val detail = buildProblemDetail(ErrorCode.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.message, request.requestURI)
        return ResponseEntity.internalServerError().body(detail)
    }

    private fun buildProblemDetail(errorCode: ErrorCode, message: String, uri: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(errorCode.httpStatus, message).apply {
            title = errorCode.name
            instance = URI.create(uri)
        }

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}

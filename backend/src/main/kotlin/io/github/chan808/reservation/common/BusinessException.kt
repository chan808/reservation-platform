package io.github.chan808.reservation.common

open class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)

class AuthException(
    errorCode: ErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null,
) : BusinessException(errorCode, message, cause)

class MemberException(
    errorCode: ErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null,
) : BusinessException(errorCode, message, cause)

class ProductException(
    errorCode: ErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null,
) : BusinessException(errorCode, message, cause)

class OrderException(
    errorCode: ErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null,
) : BusinessException(errorCode, message, cause)

class PaymentException(
    errorCode: ErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null,
) : BusinessException(errorCode, message, cause)

class RateLimitException(val retryAfterSeconds: Long) : BusinessException(ErrorCode.TOO_MANY_REQUESTS)

package io.github.chan808.reservation.payment.api

interface PaymentApi {
    fun preparePayment(command: PaymentCommand): PaymentExecutionResult
    fun confirmPayment(command: PaymentConfirmCommand): PaymentExecutionResult
    fun cancelPayment(orderId: Long, reason: String): PaymentExecutionResult?
    fun getPayment(orderId: Long): PaymentExecutionResult?
    fun handleWebhook(provider: PaymentMethodType, request: PaymentWebhookRequest)
}

data class PaymentCommand(
    val orderId: Long,
    val orderNumber: String,
    val paymentType: PaymentMethodType,
    val amount: Long,
    val buyerEmail: String,
)

data class PaymentExecutionResult(
    val paymentId: String?,
    val redirectUrl: String?,
    val status: PaymentStatusView,
    val reason: String? = null,
)

data class PaymentConfirmCommand(
    val orderId: Long,
    val orderNumber: String,
    val paymentKey: String,
    val amount: Long,
)

data class PaymentWebhookRequest(
    val eventId: String?,
    val paymentId: String?,
    val orderId: Long?,
    val status: PaymentStatusView,
    val reason: String? = null,
    val payload: Map<String, Any?> = emptyMap(),
)

enum class PaymentMethodType {
    TOSS,
    KAKAO,
    PAYPAL,
}

enum class PaymentStatusView {
    READY,
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELED,
}

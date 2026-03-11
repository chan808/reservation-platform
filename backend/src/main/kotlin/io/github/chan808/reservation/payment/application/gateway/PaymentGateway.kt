package io.github.chan808.reservation.payment.application.gateway

import io.github.chan808.reservation.payment.api.PaymentCommand
import io.github.chan808.reservation.payment.api.PaymentMethodType
import io.github.chan808.reservation.payment.api.PaymentStatusView

interface PaymentGateway {
    val type: PaymentMethodType
    fun prepare(command: PaymentCommand): GatewayPrepareResult
    fun confirm(command: GatewayConfirmCommand): GatewayConfirmResult
    fun cancel(command: GatewayCancelCommand): GatewayCancelResult
}

data class GatewayPrepareResult(
    val paymentId: String,
    val redirectUrl: String?,
    val status: PaymentStatusView,
    val rawResponse: String?,
)

data class GatewayConfirmCommand(
    val paymentKey: String,
    val orderNumber: String,
    val amount: Long,
)

data class GatewayConfirmResult(
    val paymentId: String,
    val status: PaymentStatusView,
    val rawResponse: String?,
)

data class GatewayCancelCommand(
    val paymentKey: String,
    val reason: String,
)

data class GatewayCancelResult(
    val paymentId: String,
    val status: PaymentStatusView,
    val rawResponse: String?,
)

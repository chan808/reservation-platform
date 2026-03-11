package io.github.chan808.reservation.payment.application.gateway

import io.github.chan808.reservation.payment.api.PaymentCommand
import io.github.chan808.reservation.payment.api.PaymentMethodType
import io.github.chan808.reservation.payment.api.PaymentStatusView
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PaypalPaymentGateway : PaymentGateway {
    override val type: PaymentMethodType = PaymentMethodType.PAYPAL

    override fun prepare(command: PaymentCommand): GatewayPrepareResult {
        val paymentId = "paypal_${UUID.randomUUID()}"
        return GatewayPrepareResult(
            paymentId = paymentId,
            redirectUrl = "https://pay.paypal.local/checkout/$paymentId",
            status = PaymentStatusView.READY,
            rawResponse = """{"provider":"PAYPAL","paymentId":"$paymentId","orderNumber":"${command.orderNumber}"}""",
        )
    }

    override fun confirm(command: GatewayConfirmCommand): GatewayConfirmResult =
        GatewayConfirmResult(
            paymentId = command.paymentKey,
            status = PaymentStatusView.SUCCEEDED,
            rawResponse = """{"provider":"PAYPAL","paymentId":"${command.paymentKey}","status":"SUCCEEDED"}""",
        )

    override fun cancel(command: GatewayCancelCommand): GatewayCancelResult =
        GatewayCancelResult(
            paymentId = command.paymentKey,
            status = PaymentStatusView.CANCELED,
            rawResponse = """{"provider":"PAYPAL","paymentId":"${command.paymentKey}","status":"CANCELED"}""",
        )
}

package io.github.chan808.reservation.payment.application.gateway

import io.github.chan808.reservation.payment.api.PaymentCommand
import io.github.chan808.reservation.payment.api.PaymentMethodType
import io.github.chan808.reservation.payment.api.PaymentStatusView
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class KakaoPaymentGateway : PaymentGateway {
    override val type: PaymentMethodType = PaymentMethodType.KAKAO

    override fun prepare(command: PaymentCommand): GatewayPrepareResult {
        val paymentId = "kakao_${UUID.randomUUID()}"
        return GatewayPrepareResult(
            paymentId = paymentId,
            redirectUrl = "https://pay.kakao.local/checkout/$paymentId",
            status = PaymentStatusView.READY,
            rawResponse = """{"provider":"KAKAO","paymentId":"$paymentId","orderNumber":"${command.orderNumber}"}""",
        )
    }

    override fun confirm(command: GatewayConfirmCommand): GatewayConfirmResult =
        GatewayConfirmResult(
            paymentId = command.paymentKey,
            status = PaymentStatusView.SUCCEEDED,
            rawResponse = """{"provider":"KAKAO","paymentId":"${command.paymentKey}","status":"SUCCEEDED"}""",
        )

    override fun cancel(command: GatewayCancelCommand): GatewayCancelResult =
        GatewayCancelResult(
            paymentId = command.paymentKey,
            status = PaymentStatusView.CANCELED,
            rawResponse = """{"provider":"KAKAO","paymentId":"${command.paymentKey}","status":"CANCELED"}""",
        )
}

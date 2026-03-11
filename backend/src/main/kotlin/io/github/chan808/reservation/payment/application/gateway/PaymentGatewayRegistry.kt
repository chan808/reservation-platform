package io.github.chan808.reservation.payment.application.gateway

import io.github.chan808.reservation.common.ErrorCode
import io.github.chan808.reservation.common.PaymentException
import io.github.chan808.reservation.payment.api.PaymentMethodType
import org.springframework.stereotype.Component

@Component
class PaymentGatewayRegistry(
    gateways: List<PaymentGateway>,
) {
    private val gatewaysByType = gateways.associateBy { it.type }

    fun get(type: PaymentMethodType): PaymentGateway =
        gatewaysByType[type] ?: throw PaymentException(ErrorCode.INVALID_INPUT, "지원하지 않는 결제 공급자입니다.")
}

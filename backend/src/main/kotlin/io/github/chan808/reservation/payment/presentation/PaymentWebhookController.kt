package io.github.chan808.reservation.payment.presentation

import io.github.chan808.reservation.common.ApiResponse
import io.github.chan808.reservation.payment.api.PaymentApi
import io.github.chan808.reservation.payment.api.PaymentMethodType
import io.github.chan808.reservation.payment.api.PaymentWebhookRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments/webhooks")
class PaymentWebhookController(
    private val paymentApi: PaymentApi,
) {

    @PostMapping("/{provider}")
    fun handle(
        @PathVariable provider: PaymentMethodType,
        @RequestBody @Valid request: PaymentWebhookRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        paymentApi.handleWebhook(provider, request)
        return ResponseEntity.ok(ApiResponse.success())
    }
}

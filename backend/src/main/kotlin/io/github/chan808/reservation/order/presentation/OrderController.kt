package io.github.chan808.reservation.order.presentation

import io.github.chan808.reservation.common.ApiResponse
import io.github.chan808.reservation.order.application.OrderService
import io.github.chan808.reservation.payment.api.PaymentExecutionResult
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService,
) {

    @PostMapping
    fun create(
        @AuthenticationPrincipal memberId: Long,
        @RequestBody @Valid request: CreateOrderRequest,
    ): ResponseEntity<ApiResponse<OrderResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(orderService.create(memberId, request)))

    @GetMapping("/{orderId}")
    fun get(
        @AuthenticationPrincipal memberId: Long,
        @PathVariable orderId: Long,
    ): ResponseEntity<ApiResponse<OrderResponse>> =
        ResponseEntity.ok(ApiResponse.of(orderService.get(memberId, orderId)))

    @PostMapping("/{orderId}/cancel")
    fun cancel(
        @AuthenticationPrincipal memberId: Long,
        @PathVariable orderId: Long,
        @RequestBody(required = false) request: CancelOrderRequest?,
    ): ResponseEntity<ApiResponse<OrderResponse>> =
        ResponseEntity.ok(ApiResponse.of(orderService.cancel(memberId, orderId, request ?: CancelOrderRequest(null))))

    @PostMapping("/{orderId}/confirm-payment")
    fun confirmPayment(
        @AuthenticationPrincipal memberId: Long,
        @PathVariable orderId: Long,
        @RequestBody @Valid request: ConfirmOrderPaymentRequest,
    ): ResponseEntity<ApiResponse<PaymentExecutionResult>> =
        ResponseEntity.ok(ApiResponse.of(orderService.confirmPayment(memberId, orderId, request)))
}

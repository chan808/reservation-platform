package io.github.chan808.reservation.payment.application.gateway

import io.github.chan808.reservation.common.ErrorCode
import io.github.chan808.reservation.common.PaymentException
import io.github.chan808.reservation.payment.api.PaymentCommand
import io.github.chan808.reservation.payment.api.PaymentMethodType
import io.github.chan808.reservation.payment.api.PaymentStatusView
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.Base64
import java.util.UUID

@Component
class TossPaymentGateway(
    private val properties: TossPaymentProperties,
    private val objectMapper: ObjectMapper,
) : PaymentGateway {
    override val type: PaymentMethodType = PaymentMethodType.TOSS
    private val log = LoggerFactory.getLogger(TossPaymentGateway::class.java)
    private val restClient = RestClient.builder()
        .baseUrl(properties.baseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuthHeader(properties.secretKey))
        .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .defaultHeader("Accept-Language", "en")
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(3_000)
                setReadTimeout(5_000)
            },
        )
        .build()

    override fun prepare(command: PaymentCommand): GatewayPrepareResult {
        val paymentId = "toss_${UUID.randomUUID()}"
        return GatewayPrepareResult(
            paymentId = paymentId,
            redirectUrl = null,
            status = PaymentStatusView.READY,
            rawResponse = """{"provider":"TOSS","paymentId":"$paymentId","orderNumber":"${command.orderNumber}","clientKey":"${properties.clientKey}"}""",
        )
    }

    override fun confirm(command: GatewayConfirmCommand): GatewayConfirmResult {
        if (properties.secretKey.isBlank()) {
            throw PaymentException(ErrorCode.PAYMENT_CONFIRM_FAILED, "토스 시크릿 키가 설정되지 않았습니다.")
        }

        val response = post(
            "/v1/payments/confirm",
            mapOf(
                "paymentKey" to command.paymentKey,
                "orderId" to command.orderNumber,
                "amount" to command.amount,
            ),
        )
        val paymentKey = response.path("paymentKey").asText(command.paymentKey)
        val status = response.path("status").asText()
        return GatewayConfirmResult(
            paymentId = paymentKey,
            status = status.toPaymentStatus(),
            rawResponse = response.toPrettyString(),
        )
    }

    override fun cancel(command: GatewayCancelCommand): GatewayCancelResult {
        if (properties.secretKey.isBlank()) {
            throw PaymentException(ErrorCode.PAYMENT_CANNOT_CANCEL, "토스 시크릿 키가 설정되지 않았습니다.")
        }

        val response = post(
            "/v1/payments/${command.paymentKey}/cancel",
            mapOf("cancelReason" to command.reason),
        )
        return GatewayCancelResult(
            paymentId = response.path("paymentKey").asText(command.paymentKey),
            status = PaymentStatusView.CANCELED,
            rawResponse = response.toPrettyString(),
        )
    }

    private fun post(path: String, body: Map<String, Any?>): JsonNode =
        try {
            val responseBody = restClient.post()
                .uri(path)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(body)
                .retrieve()
                .body(String::class.java)
                ?: throw PaymentException(ErrorCode.PAYMENT_CONFIRM_FAILED)
            objectMapper.readTree(responseBody)
        } catch (ex: RestClientResponseException) {
            log.warn("Toss payment API error status={} body={}", ex.statusCode.value(), ex.responseBodyAsString)
            throw PaymentException(ErrorCode.PAYMENT_CONFIRM_FAILED, ex.responseBodyAsString.ifBlank { ex.message ?: ErrorCode.PAYMENT_CONFIRM_FAILED.message }, ex)
        } catch (ex: PaymentException) {
            throw ex
        } catch (ex: Exception) {
            throw PaymentException(ErrorCode.PAYMENT_CONFIRM_FAILED, ex.message ?: ErrorCode.PAYMENT_CONFIRM_FAILED.message, ex)
        }

    private fun basicAuthHeader(secretKey: String): String {
        val encoded = Base64.getEncoder().encodeToString("$secretKey:".toByteArray())
        return "Basic $encoded"
    }
}

private fun String.toPaymentStatus(): PaymentStatusView =
    when (this.uppercase()) {
        "DONE" -> PaymentStatusView.SUCCEEDED
        "CANCELED" -> PaymentStatusView.CANCELED
        "ABORTED", "EXPIRED" -> PaymentStatusView.FAILED
        "IN_PROGRESS", "READY", "WAITING_FOR_DEPOSIT" -> PaymentStatusView.PENDING
        else -> PaymentStatusView.FAILED
    }

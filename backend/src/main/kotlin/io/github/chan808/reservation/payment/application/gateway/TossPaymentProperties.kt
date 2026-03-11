package io.github.chan808.reservation.payment.application.gateway

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "payment.toss")
data class TossPaymentProperties(
    val secretKey: String = "",
    val clientKey: String = "",
    val baseUrl: String = "https://api.tosspayments.com",
)

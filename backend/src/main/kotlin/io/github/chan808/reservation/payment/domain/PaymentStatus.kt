package io.github.chan808.reservation.payment.domain

enum class PaymentStatus {
    READY,
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELED,
}

package io.github.chan808.reservation.order.domain

enum class OrderStatus {
    PENDING_PAYMENT,
    PAYMENT_PROCESSING,
    PAID,
    PAYMENT_FAILED,
    CANCELED,
    EXPIRED,
}

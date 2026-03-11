package io.github.chan808.reservation.order.domain

enum class OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PAYMENT_FAILED,
    CANCELED,
    EXPIRED,
}

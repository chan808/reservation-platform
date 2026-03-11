package io.github.chan808.reservation.payment.infrastructure.persistence

import io.github.chan808.reservation.payment.domain.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findByOrderId(orderId: Long): Payment?
    fun findByPaymentKey(paymentKey: String): Payment?
}

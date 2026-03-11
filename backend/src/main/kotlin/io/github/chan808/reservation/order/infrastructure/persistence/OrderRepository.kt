package io.github.chan808.reservation.order.infrastructure.persistence

import io.github.chan808.reservation.order.domain.Order
import io.github.chan808.reservation.order.domain.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long> {
    fun findByOrderRequestId(orderRequestId: String): Order?
    fun findByIdAndMemberId(id: Long, memberId: Long): Order?
    fun findAllByStatusAndPaymentDeadlineAtBefore(status: OrderStatus, deadline: java.time.LocalDateTime): List<Order>
    fun existsByMemberIdAndProductIdAndStatusIn(
        memberId: Long,
        productId: Long,
        statuses: Collection<OrderStatus>,
    ): Boolean
}

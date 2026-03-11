package io.github.chan808.reservation.order.infrastructure.persistence

import io.github.chan808.reservation.order.domain.OrderStatusHistory
import org.springframework.data.jpa.repository.JpaRepository

interface OrderStatusHistoryRepository : JpaRepository<OrderStatusHistory, Long>

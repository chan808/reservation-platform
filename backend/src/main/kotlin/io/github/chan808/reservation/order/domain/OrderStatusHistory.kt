package io.github.chan808.reservation.order.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "order_status_histories")
class OrderStatusHistory(
    @Column(nullable = false)
    val orderId: Long,

    @Column(nullable = true, length = 30)
    val fromStatus: String? = null,

    @Column(nullable = false, length = 30)
    val toStatus: String,

    @Column(nullable = true, length = 255)
    val reason: String? = null,

    @Column(nullable = false, length = 30)
    val actorType: String,

    @Column(nullable = true, length = 64)
    val actorId: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
)

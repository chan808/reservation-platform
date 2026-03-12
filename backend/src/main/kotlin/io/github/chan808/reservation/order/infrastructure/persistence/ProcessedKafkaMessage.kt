package io.github.chan808.reservation.order.infrastructure.persistence

import io.github.chan808.reservation.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "processed_kafka_messages")
class ProcessedKafkaMessage(
    @Column(nullable = false, length = 120)
    val consumerGroup: String,

    @Column(nullable = false, length = 64)
    val eventId: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
) : BaseEntity()

package io.github.chan808.reservation.order.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ProcessedKafkaMessageRepository : JpaRepository<ProcessedKafkaMessage, Long>

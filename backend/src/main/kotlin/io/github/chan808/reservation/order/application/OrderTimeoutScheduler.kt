package io.github.chan808.reservation.order.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class OrderTimeoutScheduler(
    private val orderService: OrderService,
    @Value("\${app.order.timeout.enabled:true}") private val enabled: Boolean,
) {

    private val log = LoggerFactory.getLogger(OrderTimeoutScheduler::class.java)

    @Scheduled(cron = "\${app.order.timeout.cron:0 * * * * *}")
    fun expireTimedOutOrders() {
        if (!enabled) return
        val count = orderService.expireTimedOutOrders()
        if (count > 0) {
            log.info("[ORDER] expired timed out orders count={}", count)
        }
    }
}

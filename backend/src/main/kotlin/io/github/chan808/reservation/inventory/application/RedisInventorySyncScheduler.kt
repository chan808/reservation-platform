package io.github.chan808.reservation.inventory.application

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["app.inventory.mode"], havingValue = "redis")
class RedisInventorySyncScheduler(
    private val redisTemplate: StringRedisTemplate,
    private val redisInventoryPersistenceService: RedisInventoryPersistenceService,
) {

    private val log = LoggerFactory.getLogger(RedisInventorySyncScheduler::class.java)

    @Scheduled(
        fixedDelayString = "\${app.inventory.redis.sync-delay-ms:1000}",
        initialDelayString = "\${app.inventory.redis.sync-delay-ms:1000}",
    )
    fun syncDirtyStocks() {
        repeat(100) {
            val rawProductId = redisTemplate.opsForSet().pop(RedisInventoryKeys.DIRTY_PRODUCTS_KEY) ?: return
            val productId = rawProductId.toLongOrNull() ?: return@repeat

            try {
                redisInventoryPersistenceService.syncDirtyProduct(productId)
            } catch (ex: RuntimeException) {
                redisTemplate.opsForSet().add(RedisInventoryKeys.DIRTY_PRODUCTS_KEY, rawProductId)
                log.warn("[INVENTORY] failed to sync dirty stock productId={}", productId, ex)
            }
        }
    }
}

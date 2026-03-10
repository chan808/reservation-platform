package io.github.chan808.authtemplate.common.ratelimit

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RateLimiter(private val redisTemplate: StringRedisTemplate) {

    private val script = DefaultRedisScript(
        """
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
        end
        return count
        """.trimIndent(),
        Long::class.java,
    )

    fun retryAfterIfExceeded(key: String, ttlSeconds: Long, limit: Int): Long? {
        val count = redisTemplate.execute(script, listOf(key), ttlSeconds.toString()) ?: 0L
        if (count <= limit) return null
        return redisTemplate.getExpire(key, TimeUnit.SECONDS).coerceAtLeast(1L)
    }
}

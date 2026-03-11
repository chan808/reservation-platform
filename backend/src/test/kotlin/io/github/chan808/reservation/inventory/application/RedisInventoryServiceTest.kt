package io.github.chan808.reservation.inventory.application

import io.github.chan808.reservation.common.ProductException
import io.github.chan808.reservation.inventory.api.StockReservationCommand
import io.github.chan808.reservation.product.stock.ProductStockPort
import io.github.chan808.reservation.product.stock.ProductStockSnapshot
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime
import kotlin.test.assertEquals

@Tag("integration")
@Testcontainers
class RedisInventoryServiceTest {

    companion object {
        @Container
        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:8-alpine")
            .withExposedPorts(6379)
    }

    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var productStockPort: ProductStockPort
    private lateinit var redisInventoryService: RedisInventoryService

    @BeforeEach
    fun setup() {
        connectionFactory = LettuceConnectionFactory(redis.host, redis.getMappedPort(6379))
        connectionFactory.afterPropertiesSet()

        redisTemplate = StringRedisTemplate(connectionFactory)
        redisTemplate.afterPropertiesSet()

        productStockPort = mockk()
        redisInventoryService = RedisInventoryService(redisTemplate, productStockPort)
    }

    @AfterEach
    fun cleanup() {
        redisTemplate.connectionFactory?.connection?.flushAll()
        connectionFactory.destroy()
    }

    @Test
    fun `reserveStock loads snapshot into redis and decrements atomically`() {
        every { productStockPort.findSnapshot(1L) } returns ProductStockSnapshot(
            productId = 1L,
            unitPrice = 12000L,
            availableStock = 5,
            saleStartAt = LocalDateTime.now().minusMinutes(1),
            saleEndAt = LocalDateTime.now().plusMinutes(10),
        )

        val first = redisInventoryService.reserveStock(StockReservationCommand(1L, 2))
        val second = redisInventoryService.reserveStock(StockReservationCommand(1L, 1))

        assertEquals(3, first.remainingStock)
        assertEquals(2, second.remainingStock)
        assertEquals(12000L, second.unitPrice)
    }

    @Test
    fun `releaseStock restores redis stock`() {
        every { productStockPort.findSnapshot(1L) } returns ProductStockSnapshot(
            productId = 1L,
            unitPrice = 12000L,
            availableStock = 2,
            saleStartAt = LocalDateTime.now().minusMinutes(1),
            saleEndAt = LocalDateTime.now().plusMinutes(10),
        )

        redisInventoryService.reserveStock(StockReservationCommand(1L, 1))
        redisInventoryService.releaseStock(1L, 1)

        val stock = redisTemplate.opsForHash()
            .get(RedisInventoryKeys.productKey(1L), "stockQuantity")
            ?.toString()

        assertEquals("2", stock)
    }

    @Test
    fun `reserveStock throws when there is not enough stock`() {
        every { productStockPort.findSnapshot(1L) } returns ProductStockSnapshot(
            productId = 1L,
            unitPrice = 12000L,
            availableStock = 1,
            saleStartAt = LocalDateTime.now().minusMinutes(1),
            saleEndAt = LocalDateTime.now().plusMinutes(10),
        )

        assertThrows<ProductException> {
            redisInventoryService.reserveStock(StockReservationCommand(1L, 2))
        }
    }
}

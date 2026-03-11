package io.github.chan808.reservation.product.infrastructure.persistence

import io.github.chan808.reservation.product.domain.Product
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface ProductRepository : JpaRepository<Product, Long> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Product>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    fun findByIdForUpdate(id: Long): Product?
}

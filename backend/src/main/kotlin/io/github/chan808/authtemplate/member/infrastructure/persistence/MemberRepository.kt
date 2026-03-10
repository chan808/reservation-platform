package io.github.chan808.authtemplate.member.infrastructure.persistence

import io.github.chan808.authtemplate.member.domain.Member
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByEmail(email: String): Member?
    fun existsByEmail(email: String): Boolean
    fun findByProviderAndProviderId(provider: String, providerId: String): Member?
    fun findAllByEmailVerifiedFalseAndProviderIsNullAndCreatedAtBefore(cutoff: LocalDateTime): List<Member>
}

package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.MemberException
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.events.MemberWithdrawnEvent
import io.github.chan808.authtemplate.member.events.PasswordChangedEvent
import io.github.chan808.authtemplate.member.infrastructure.persistence.MemberRepository
import io.github.chan808.authtemplate.member.infrastructure.security.BreachedPasswordChecker
import io.github.chan808.authtemplate.member.presentation.ChangePasswordRequest
import io.github.chan808.authtemplate.member.presentation.MemberResponse
import io.github.chan808.authtemplate.member.presentation.SignupRequest
import io.github.chan808.authtemplate.member.presentation.UpdateProfileRequest
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MemberCommandService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val signupRateLimitService: SignupRateLimitService,
    private val breachedPasswordChecker: BreachedPasswordChecker,
    private val emailVerificationService: EmailVerificationService,
    private val eventPublisher: ApplicationEventPublisher,
    private val domainMetrics: DomainMetrics,
) {
    private val log = LoggerFactory.getLogger(MemberCommandService::class.java)

    @Transactional
    fun signup(request: SignupRequest, ip: String): MemberResponse {
        signupRateLimitService.check(ip)
        val email = request.email.lowercase().trim()
        memberRepository.findByEmail(email)?.let { existing ->
            if (existing.emailVerified || existing.isOAuthAccount) {
                domainMetrics.recordSignupFailure("duplicate_email")
                throw MemberException(ErrorCode.EMAIL_ALREADY_EXISTS)
            }

            breachedPasswordChecker.check(request.password, email)
            existing.changePassword(passwordEncoder.encode(request.password) ?: error("PasswordEncoder returned null"))
            emailVerificationService.sendVerification(existing.id, existing.email)
            domainMetrics.recordSignupSuccess()
            log.info("[AUTH] unverified signup retried memberId={}", existing.id)
            return MemberResponse.from(existing)
        }

        breachedPasswordChecker.check(request.password, email)
        val member = memberRepository.save(
            Member(
                email = email,
                password = passwordEncoder.encode(request.password) ?: error("PasswordEncoder returned null"),
            ),
        )
        emailVerificationService.sendVerification(member.id, member.email)
        domainMetrics.recordSignupSuccess()
        return MemberResponse.from(member)
    }

    @Transactional
    fun updateProfile(memberId: Long, request: UpdateProfileRequest): MemberResponse {
        val member = getById(memberId)
        member.updateProfile(request.nickname)
        domainMetrics.recordProfileUpdate()
        log.info("[MEMBER] profile updated memberId={}", memberId)
        return MemberResponse.from(member)
    }

    @Transactional
    fun changePassword(memberId: Long, request: ChangePasswordRequest) {
        val member = getById(memberId)
        if (!passwordEncoder.matches(request.currentPassword, member.password)) {
            throw MemberException(ErrorCode.INVALID_CURRENT_PASSWORD)
        }
        breachedPasswordChecker.check(request.newPassword, member.email)
        member.changePassword(passwordEncoder.encode(request.newPassword) ?: error("PasswordEncoder returned null"))
        eventPublisher.publishEvent(PasswordChangedEvent(memberId))
        domainMetrics.recordPasswordChange()
        log.info("[AUTH] password changed memberId={}", memberId)
    }

    @Transactional
    fun withdraw(memberId: Long) {
        val member = getById(memberId)
        eventPublisher.publishEvent(MemberWithdrawnEvent(memberId))
        memberRepository.delete(member)
        domainMetrics.recordWithdrawal()
        log.info("[MEMBER] member withdrawn memberId={}", memberId)
    }

    fun getById(memberId: Long): Member =
        memberRepository.findById(memberId).orElseThrow { MemberException(ErrorCode.MEMBER_NOT_FOUND) }

    fun getMyInfo(memberId: Long): MemberResponse = MemberResponse.from(getById(memberId))
}

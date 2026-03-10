package io.github.chan808.authtemplate.member.application

import io.github.chan808.authtemplate.common.ErrorCode
import io.github.chan808.authtemplate.common.MemberException
import io.github.chan808.authtemplate.common.metrics.DomainMetrics
import io.github.chan808.authtemplate.member.application.EmailVerificationService
import io.github.chan808.authtemplate.member.application.MemberCommandService
import io.github.chan808.authtemplate.member.application.SignupRateLimitService
import io.github.chan808.authtemplate.member.presentation.SignupRequest
import io.github.chan808.authtemplate.member.domain.Member
import io.github.chan808.authtemplate.member.infrastructure.persistence.MemberRepository
import io.github.chan808.authtemplate.member.infrastructure.security.BreachedPasswordChecker
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional
import kotlin.test.assertEquals

class MemberCommandServiceTest {

    private val memberRepository: MemberRepository = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()
    private val signupRateLimitService: SignupRateLimitService = mockk()
    private val breachedPasswordChecker: BreachedPasswordChecker = mockk()
    private val emailVerificationService: EmailVerificationService = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val domainMetrics: DomainMetrics = mockk(relaxed = true)
    private val memberCommandService = MemberCommandService(
        memberRepository,
        passwordEncoder,
        signupRateLimitService,
        breachedPasswordChecker,
        emailVerificationService,
        eventPublisher,
        domainMetrics,
    )

    @Test
    fun `duplicate email throws email already exists`() {
        every { signupRateLimitService.check(any()) } just Runs
        every {
            memberRepository.findByEmail("test@example.com")
        } returns Member(email = "test@example.com", password = "encoded", emailVerified = true, id = 1L)

        val ex = assertThrows<MemberException> {
            memberCommandService.signup(SignupRequest("test@example.com", "Password1!"), "127.0.0.1")
        }
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, ex.errorCode)
    }

    @Test
    fun `email is normalized to lowercase on signup`() {
        every { signupRateLimitService.check(any()) } just Runs
        every { memberRepository.findByEmail("test@example.com") } returns null
        every { breachedPasswordChecker.check(any(), any()) } just Runs
        every { passwordEncoder.encode(any()) } returns "encoded"
        every { memberRepository.save(any()) } answers {
            firstArg<Member>().let { it.copyForTest(id = 1L) }
        }
        every { emailVerificationService.sendVerification(any(), any()) } just Runs

        memberCommandService.signup(SignupRequest("TEST@EXAMPLE.COM", "Password1!"), "127.0.0.1")

        verify { memberRepository.save(match { it.email == "test@example.com" }) }
    }

    @Test
    fun `unverified local account can sign up again and receives a new verification mail`() {
        val existing = Member(
            email = "test@example.com",
            password = "old-encoded",
            emailVerified = false,
            id = 1L,
        )
        every { signupRateLimitService.check(any()) } just Runs
        every { memberRepository.findByEmail("test@example.com") } returns existing
        every { breachedPasswordChecker.check(any(), any()) } just Runs
        every { passwordEncoder.encode("Password1!") } returns "new-encoded"
        every { emailVerificationService.sendVerification(1L, "test@example.com") } just Runs

        val response = memberCommandService.signup(SignupRequest("test@example.com", "Password1!"), "127.0.0.1")

        assertEquals(existing.id, response.id)
        assertEquals("new-encoded", existing.password)
        verify { emailVerificationService.sendVerification(1L, "test@example.com") }
        verify(exactly = 0) { memberRepository.save(any()) }
    }

    @Test
    fun `missing member id throws member not found`() {
        every { memberRepository.findById(999L) } returns Optional.empty()

        val ex = assertThrows<MemberException> { memberCommandService.getById(999L) }
        assertEquals(ErrorCode.MEMBER_NOT_FOUND, ex.errorCode)
    }

    private fun Member.copyForTest(id: Long): Member = Member(
        email = this.email,
        password = this.password,
        emailVerified = this.emailVerified,
        provider = this.provider,
        providerId = this.providerId,
        nickname = this.nickname,
        role = this.role,
        id = id,
    )
}

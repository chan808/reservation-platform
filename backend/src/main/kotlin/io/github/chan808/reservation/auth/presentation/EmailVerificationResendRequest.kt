package io.github.chan808.reservation.auth.presentation

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class EmailVerificationResendRequest(
    @field:Email
    @field:NotBlank
    val email: String,
)

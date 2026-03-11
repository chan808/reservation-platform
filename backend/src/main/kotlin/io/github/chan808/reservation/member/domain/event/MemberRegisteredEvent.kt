package io.github.chan808.reservation.member.domain.event

data class MemberRegisteredEvent(val email: String, val verificationToken: String)

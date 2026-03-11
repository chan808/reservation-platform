@file:Suppress("unused")
package io.github.chan808.reservation.member
import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = ["common", "common :: metrics", "common :: ratelimit"],
)
class MemberModule

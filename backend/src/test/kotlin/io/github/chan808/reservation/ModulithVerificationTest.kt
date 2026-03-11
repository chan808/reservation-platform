package io.github.chan808.reservation

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class ModulithVerificationTest {

    @Test
    fun `module boundaries are respected`() {
        ApplicationModules.of(ReservationApplication::class.java).verify()
    }
}

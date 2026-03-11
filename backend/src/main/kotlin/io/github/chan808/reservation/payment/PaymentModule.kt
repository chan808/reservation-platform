@file:Suppress("unused")

package io.github.chan808.reservation.payment

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = ["order :: events", "common", "common :: metrics"],
)
class PaymentModule

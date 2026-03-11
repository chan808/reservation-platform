@file:Suppress("unused")

package io.github.chan808.reservation.product

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = ["common", "common :: metrics"],
)
class ProductModule

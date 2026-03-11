@file:Suppress("unused")

package io.github.chan808.reservation.order

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = ["member :: api", "inventory :: api", "payment :: api", "common"],
)
class OrderModule

@file:Suppress("unused")

package io.github.chan808.reservation.order

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = ["member :: api", "product :: api", "payment :: api", "payment :: events", "common"],
)
class OrderModule

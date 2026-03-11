@file:Suppress("unused")

package io.github.chan808.reservation.inventory

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    allowedDependencies = ["product :: stock", "common"],
)
class InventoryModule

package io.github.chan808.reservation.inventory.application

object RedisInventoryKeys {
    const val DIRTY_PRODUCTS_KEY = "inventory:dirty-products"

    fun productKey(productId: Long): String = "inventory:product:$productId"
}

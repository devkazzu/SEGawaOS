package com.mio.os.core

/**
 * Pure home-screen ordering helpers.
 *
 * The launcher persists the user's arrangement as an ordered list of package
 * names ([MioSettings.homeOrder]). Apps the user never arranged (new installs)
 * are appended after the arranged ones, so nothing ever disappears.
 */
object HomeLayout {

    /** Stable order: known keys in [order], then any remaining keys as given. */
    fun orderKeys(keys: List<String>, order: List<String>): List<String> {
        if (order.isEmpty()) return keys
        val pool = keys.toSet()
        val seen = LinkedHashSet<String>()
        val out = ArrayList<String>(keys.size)
        order.forEach { k -> if (k in pool && seen.add(k)) out += k }
        keys.forEach { k -> if (seen.add(k)) out += k }
        return out
    }

    /** Moves the item at [from] to [to]; out-of-range input returns [list]. */
    fun move(list: List<String>, from: Int, to: Int): List<String> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        val m = list.toMutableList()
        val item = m.removeAt(from)
        m.add(to, item)
        return m
    }
}

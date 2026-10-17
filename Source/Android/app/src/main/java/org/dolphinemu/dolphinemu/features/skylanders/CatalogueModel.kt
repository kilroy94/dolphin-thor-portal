// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders

import androidx.annotation.Keep

@Keep data class CatalogueEntry(
    val id: Int, val variant: Int, val name: String, val game: Int, val type: Int, val element: Int
) {
    val key: Long get() = (id.toLong() shl 16) or variant.toLong()
    fun description() = "$name [$id/$variant]\n${CatalogueModel.games.getOrNull(game) ?: "Unknown category"} / ${CatalogueModel.types.getOrNull(type) ?: "Unknown type"} / ${CatalogueModel.elements.getOrNull(element) ?: "Unknown element"}"
}

data class CatalogueFilter(
    val query: String = "", val games: Set<Int> = (0..4).toSet(), val type: Int = 0, val element: Int = 0,
    val selectedTypes: Set<Int> = (1..10).toSet(), val selectedElements: Set<Int> = (1..11).toSet(),
    val sortByElement: Boolean = true
)

object CatalogueModel {
    val games = listOf("Spyro's Adventure", "Giants", "Swap Force", "Trap Team", "SuperChargers")
    val types = listOf("All types", "Skylander", "Giant", "Swapper", "Trap Master", "Mini", "Item", "Trophy", "Vehicle", "Trap", "Unknown type")
    val elements = listOf("All elements", "Magic", "Fire", "Air", "Life", "Undead", "Earth", "Water", "Tech", "Dark", "Light", "Other")

    fun matches(entry: CatalogueEntry, filter: CatalogueFilter): Boolean =
        entry.game in filter.games && entry.type in filter.selectedTypes && entry.element in filter.selectedElements &&
            (filter.type == 0 || entry.type == filter.type) &&
            (filter.element == 0 || entry.element == filter.element) &&
            (filter.query.isEmpty() || entry.name.contains(filter.query, ignoreCase = true) ||
                "${entry.id}/${entry.variant}".contains(filter.query, ignoreCase = true))

    fun search(entries: List<CatalogueEntry>, filter: CatalogueFilter): List<CatalogueEntry> =
        entries.filter { matches(it, filter) }.sortedWith(order(filter))

    fun order(filter: CatalogueFilter): Comparator<CatalogueEntry> =
        compareBy<CatalogueEntry> { if (filter.sortByElement) it.element else 0 }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id }.thenBy { it.variant }

    /** Unknown imports have no assigned game/type/element; default All leaves them visible. */
    fun showUnclassified(filter: CatalogueFilter): Boolean =
        filter.games == (0..4).toSet() && filter.type == 0 && filter.element == 0 &&
            filter.selectedTypes == (1..10).toSet() && filter.selectedElements == (1..11).toSet()

    fun matchesCopy(entry: CatalogueEntry?, name: String, fileName: String, id: Int, variant: Int,
                    filter: CatalogueFilter, unknownOnly: Boolean = false): Boolean {
        if (unknownOnly && entry != null) return false
        val query = filter.query
        val textMatch = query.isEmpty() || name.contains(query, true) || fileName.contains(query, true) ||
            "$id/$variant".contains(query, true)
        if (!textMatch) return false
        if (unknownOnly) return true
        return if (entry == null) showUnclassified(filter)
        else matches(entry, filter.copy(query = ""))
    }
}

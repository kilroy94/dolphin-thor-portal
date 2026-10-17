// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders

import java.text.Normalizer
import java.util.Locale

data class FigureArtwork(val game: Int, val path: String) {
    val subject: String get() = path.substringAfterLast('/').substringBeforeLast('.')
        .removePrefix("Sobersu's ").removePrefix("Mr Shadow's ")
        .removePrefix("Vehicle ")
        .replace(Regex("(?i)\\btrap_"), " ")
        .replace(Regex("(?i)_(back|giant|mini)(?=_|$)"), " ")
}
data class ArtworkMatch(val image: FigureArtwork, val exact: Boolean)

/** Order-independent names handle native suffix variants and artwork prefix variants. */
object ArtworkMatcher {
    // Artwork inventory can include games not supported by this native catalogue.
    val games = CatalogueModel.games + "Imaginators"
    fun signature(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT)
        .replace(Regex("\\bseries\\s*([234])\\b|\\bs([234])\\b")) {
            " series${it.groupValues[1].ifEmpty { it.groupValues[2] }} "
        }.replace("lightcore", "lc").replace("captain's", "captains")
        .replace("dragon's", "dragons").replace("dragon_s", "dragons")
        .split(Regex("[^a-z0-9]+" )).filter { it.isNotEmpty() }.sorted().joinToString(" ")

    fun find(entry: CatalogueEntry, images: List<FigureArtwork>): ArtworkMatch? {
        val signature = signature(entry.name)
        val sameGame = images.filter { it.game == entry.game }
        sameGame.firstOrNull { signature(it.subject) == signature }?.let { return ArtworkMatch(it, true) }
        // Named reposes put the character first in native names and last in card names.
        val segments = Regex("\\(([^)]+)\\)").findAll(entry.name).map { it.groupValues[1] }.toList()
        val base = entry.name.substringBefore(" (")
        val repose = segments.filterNot { it in listOf("Top", "Bottom") || it.matches(Regex("S[234]")) }
        val half = segments.filter { it == "Top" || it == "Bottom" }.joinToString(" ")
        val alternative = signature((repose + base + half).joinToString(" "))
        sameGame.firstOrNull { signature(it.subject.replace(Regex("(?i)\\(Series [234]\\)"), "")) == alternative }
            ?.let { return ArtworkMatch(it, true) }
        // Unpictured chase variants reuse their base card, explicitly marked as base artwork.
        val baseSignature = signature("$base $half")
        sameGame.firstOrNull { signature(it.subject) == baseSignature }?.let { return ArtworkMatch(it, false) }
        if (entry.game == 4) {
            // Unpictured cosmetic variants use their named repose, not the original figure.
            val namedBase = signature((repose.map {
                it.replace(Regex("\\b(Instant|Bronze|Patina)\\b"), " ")
            } + base + half).joinToString(" "))
            sameGame.firstOrNull { signature(it.subject) == namedBase }?.let { return ArtworkMatch(it, false) }
        }
        return null
    }
}

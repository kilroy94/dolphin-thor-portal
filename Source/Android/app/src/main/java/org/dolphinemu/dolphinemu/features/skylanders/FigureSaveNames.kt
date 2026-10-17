// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders

import java.util.Locale

/** Friendly portable filenames; native exclusive creation is the final overwrite guard. */
object FigureSaveNames {
    fun stem(requested: String, fallback: String): String {
        fun clean(value: String): String {
            var name = value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
                .replace(Regex("\\s+"), " ").trim().trim('.')
            if (name.endsWith(".sky", true)) name = name.dropLast(4).trim().trim('.')
            name = name.take(80).trim().trim('.')
            if (name.lastOrNull()?.isHighSurrogate() == true) name = name.dropLast(1)
            return name
        }
        var result = clean(requested).ifEmpty { clean(fallback) }.ifEmpty { "Skylander" }
        if (Regex("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$").matches(result)) result = "$result figure"
        return result
    }

    fun available(requested: String, fallback: String, existing: Collection<String>): String {
        val name = stem(requested, fallback)
        val occupied = existing.map { it.lowercase(Locale.ROOT) }.toHashSet()
        if ("$name.sky".lowercase(Locale.ROOT) !in occupied) return "$name.sky"
        for (number in 2..10000) {
            val candidate = "$name $number.sky"
            if (candidate.lowercase(Locale.ROOT) !in occupied) return candidate
        }
        throw IllegalStateException("Too many saves with this name; choose another name")
    }
}

// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders

import androidx.annotation.Keep

@Keep data class PortalSlotState(
    val slot: Int, val present: Boolean, val status: Int, val pending: Int,
    val id: Int, val variant: Int, val serial: Long, val instance: Long, val level: Int = 0
)
@Keep data class PortalFigureInfo(val code: Int, val id: Int, val variant: Int, val serial: Long, val name: String, val level: Int = 0)
@Keep data class PortalCommandResult(val code: Int, val slot: Int, val instance: Long) {
    fun message() = when (code) {
        0 -> "Success: native slot ${slot + 1}"
        1 -> "Invalid native slot"
        2 -> "That slot is empty; refresh and try again"
        3 -> "The portal is full; remove a figure or explicitly replace one"
        4 -> "The game session has ended; reopen the portal in the current game"
        5 -> "That slot changed; select its current figure and try again"
        6 -> "Invalid figure: expected a 1024-byte Skylanders image with a valid header"
        7 -> "That figure is already on the portal; remove it before inserting another copy"
        8 -> "Cannot open or save the development copy; check storage and retry"
        else -> "Portal operation failed"
    }
}

@Keep object PortalNative {
    @JvmStatic external fun catalogue(): Array<CatalogueEntry>
    @JvmStatic external fun createFile(id: Int, variant: Int, path: String): PortalFigureInfo
    @JvmStatic external fun beginSession(): Long
    @JvmStatic external fun endSession()
    @JvmStatic external fun snapshot(session: Long): Array<PortalSlotState>?
    @JvmStatic external fun inspectFile(path: String): PortalFigureInfo
    @JvmStatic external fun insert(session: Long, replaceSlot: Int, expectedInstance: Long, path: String): PortalCommandResult
    @JvmStatic external fun remove(session: Long, slot: Int, expectedInstance: Long): PortalCommandResult
}

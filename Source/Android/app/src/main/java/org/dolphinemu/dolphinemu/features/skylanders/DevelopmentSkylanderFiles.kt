// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

object DevelopmentSkylanderFiles {
    private fun store(context: Context): FigureCopyStore {
        val userDirectory = context.getExternalFilesDir(null)
            ?: throw IOException("Development storage is unavailable")
        return FigureCopyStore(File(userDirectory, "Skylanders"))
    }

    fun importCopy(context: Context, uri: Uri): File =
        store(context).importCopy(uri.toString()) { context.contentResolver.openInputStream(uri) }

    fun newFigure(context: Context): File = store(context).newFigure()
}

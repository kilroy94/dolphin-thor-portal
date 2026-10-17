// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.dolphinemu.dolphinemu.features.secondscreen.ScreenTest
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap

/** One worker for imports, inspection, snapshots and all lower-screen mutations. */
object PortalCommands {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var session = 0L
        private set
    data class CollectionFigure(val file: File, val info: PortalFigureInfo)
    data class BrowserResult(val entries: List<CatalogueEntry>, val copies: List<CollectionFigure>)
    private data class PlacedFile(val token: Long, val instance: Long, val file: File)
    private val placedFiles = ConcurrentHashMap<Int, PlacedFile>()
    fun placedFile(token: Long, slot: PortalSlotState): File? = placedFiles[slot.slot]
        ?.takeIf { it.token == token && it.instance == slot.instance }?.file
    private var cachedCatalogue: List<CatalogueEntry>? = null // Accessed only by the worker.

    private fun catalogueOnWorker(): List<CatalogueEntry> = cachedCatalogue ?: PortalNative.catalogue().toList()
        .also { cachedCatalogue = it }

    fun catalogue(complete: (Map<Long, CatalogueEntry>) -> Unit) {
        worker.execute { val entries = catalogueOnWorker().associateBy { it.key }; main.post { complete(entries) } }
    }

    fun filter(copies: List<CollectionFigure>, filter: CatalogueFilter, unknownOnly: Boolean,
               complete: (BrowserResult) -> Unit) {
        worker.execute {
            val entries = catalogueOnWorker()
            val index = entries.associateBy { it.key }
            val result = BrowserResult(CatalogueModel.search(entries, filter), copies.filter {
                CatalogueModel.matchesCopy(if (it.info.code == 0) index[(it.info.id.toLong() shl 16) or it.info.variant.toLong()] else null,
                    it.info.name, it.file.name, it.info.id, it.info.variant, filter, unknownOnly)
            }.sortedWith(compareBy<CollectionFigure> {
                if (filter.sortByElement) index[(it.info.id.toLong() shl 16) or it.info.variant.toLong()]?.element ?: 12 else 0
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.info.name }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.file.name }))
            main.post { complete(result) }
        }
    }

    fun create(context: Context, token: Long, entry: CatalogueEntry, requestedName: String,
               complete: (CollectionFigure?, String) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            var created: CollectionFigure? = null
            val message = try {
                if (token == 0L || token != session) PortalCommandResult(4, -1, 0).message()
                else {
                    val root = directory(app)
                    if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create collection directory")
                    val destination = File(root, FigureSaveNames.available(requestedName, entry.name,
                        root.list()?.toList() ?: emptyList()))
                    val info = PortalNative.createFile(entry.id, entry.variant, destination.absolutePath)
                    if (info.code == 0) {
                        created = CollectionFigure(destination, info)
                        "Saved ${destination.nameWithoutExtension}"
                    } else PortalCommandResult(info.code, -1, 0).message()
                }
            } catch (e: IOException) { "Creation failed: ${e.message}" }
              catch (e: IllegalStateException) { e.message ?: "Choose another save name" }
              catch (_: SecurityException) { "Creation permission denied" }
            main.post { if (token == session) complete(created, message) }
        }
    }

    fun begin() {
        if (session == 0L && org.dolphinemu.dolphinemu.NativeLibrary.IsGameMetadataValid() &&
            org.dolphinemu.dolphinemu.NativeLibrary.IsEmulatingWii() &&
            org.dolphinemu.dolphinemu.features.settings.model.BooleanSetting.MAIN_EMULATE_SKYLANDER_PORTAL.boolean) {
            session = PortalNative.beginSession()
            placedFiles.clear()
            ScreenTest.record("portal session begin token=$session")
        }
    }

    fun end() {
        session = 0
        placedFiles.clear()
        PortalNative.endSession()
        ScreenTest.record("portal session end")
    }

    fun invalidate(token: Long) {
        if (session == token) {
            session = 0L
            placedFiles.clear()
            ScreenTest.record("portal session no longer valid token=$token")
        }
    }

    private fun directory(context: Context): File = File(context.getExternalFilesDir(null)
        ?: throw IOException("Development storage is unavailable"), "Skylanders").canonicalFile

    fun importCopy(context: Context, uri: Uri, complete: (String) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val message = try {
                val copy = DevelopmentSkylanderFiles.importCopy(app, uri)
                val info = PortalNative.inspectFile(copy.absolutePath)
                if (info.code == 0) "Imported development copy: ${info.name}"
                else PortalCommandResult(info.code, -1, 0).message()
            } catch (e: IOException) { "Import failed: ${e.message}" }
              catch (e: SecurityException) { "Import permission denied" }
            main.post { complete(message) }
        }
    }

    fun collection(context: Context, complete: (List<CollectionFigure>, Map<Long, List<CollectionFigure>>, String?) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            var error: String? = null
            val figures = try {
                val root = directory(app)
                root.listFiles().orEmpty().filter {
                    it.isFile && it.canonicalFile.parentFile == root &&
                        it.extension.lowercase(java.util.Locale.ROOT) in setOf("sky", "bin", "dmp", "dump")
                }
                    .map { CollectionFigure(it, PortalNative.inspectFile(it.absolutePath)) }
                    .sortedBy { it.info.name }
            } catch (e: IOException) { error = e.message; emptyList() }
              catch (e: SecurityException) { error = "Storage permission denied"; emptyList() }
            val instances = figures.filter { it.info.code == 0 }
                .groupBy { (it.info.id.toLong() shl 16) or it.info.variant.toLong() }
            main.post { complete(figures, instances, error) }
        }
    }

    fun snapshot(token: Long, complete: (Array<PortalSlotState>?) -> Unit) {
        worker.execute {
            val value = if (token != 0L && token == session) PortalNative.snapshot(token) else null
            // Always finish the read, including invalidation while it was queued.
            // The panel checks the token before accepting the snapshot.
            main.post { complete(if (token == session) value else null) }
        }
    }

    fun insert(context: Context, token: Long, file: File, target: PortalSlotState?, complete: (PortalCommandResult) -> Unit) {
        val app = context.applicationContext
        command(token, complete) {
            // New bindings only receive isolated copies, never picker URIs or original paths.
            val copy = file.canonicalFile
            val result = if (copy.parentFile != directory(app)) PortalCommandResult(8, -1, 0)
            else PortalNative.insert(token, target?.slot ?: -1, target?.instance ?: 0, copy.absolutePath)
            if (result.code == 0) {
                target?.let { placedFiles.remove(it.slot) }
                placedFiles[result.slot] = PlacedFile(token, result.instance, copy)
            }
            result
        }
    }

    fun remove(token: Long, target: PortalSlotState, complete: (PortalCommandResult) -> Unit) =
        command(token, complete) {
            PortalNative.remove(token, target.slot, target.instance).also {
                if (it.code == 0) placedFiles.remove(target.slot)
            }
        }

    private fun command(token: Long, complete: (PortalCommandResult) -> Unit, operation: () -> PortalCommandResult) {
        worker.execute {
            val result = try {
                if (token == 0L || token != session) PortalCommandResult(4, -1, 0) else operation()
            } catch (_: IOException) { PortalCommandResult(8, -1, 0) }
              catch (_: SecurityException) { PortalCommandResult(8, -1, 0) }
            ScreenTest.record("portal command code=${result.code} slot=${result.slot} instance=${result.instance}")
            main.post { if (token == session) complete(result) }
        }
    }
}

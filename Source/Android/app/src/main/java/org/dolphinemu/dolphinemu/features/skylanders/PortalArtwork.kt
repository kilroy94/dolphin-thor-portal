// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import android.widget.TextView
import android.view.View
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** JPEG I/O and decoding use a separate worker, never the portal-command queue. */
class PortalArtwork(context: Context) {
    private val assets = context.applicationContext.assets
    private val worker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(48), ThreadPoolExecutor.DiscardOldestPolicy())
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    private var images: List<FigureArtwork>? = null
    private val matches = HashMap<Long, ArtworkMatch?>()
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun show(view: ImageView, entry: CatalogueEntry?, badge: TextView) {
        view.setImageResource(android.R.drawable.ic_menu_gallery)
        badge.text = "Artwork unavailable"; badge.visibility = View.VISIBLE
        if (entry == null || closed) return
        val identity = Any(); view.tag = identity
        val target = WeakReference(view)
        worker.execute {
            if (closed) return@execute
            try {
                if (images == null) {
                    val manifest = JSONObject(assets.open("manifest.json").bufferedReader().use { it.readText() })
                    val files = manifest.getJSONArray("files")
                    images = (0 until files.length()).mapNotNull { index ->
                        val file = files.getJSONObject(index)
                        val game = ArtworkMatcher.games.indexOf(file.getString("game"))
                        if (game < 0) null else FigureArtwork(game, file.getString("localPath"))
                    }
                }
                val match = if (matches.containsKey(entry.key)) matches[entry.key]
                    else ArtworkMatcher.find(entry, images!!).also { matches[entry.key] = it }
                if (match == null) return@execute
                val bitmap = cache.get(match.image.path) ?: run {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    assets.open(match.image.path).use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (bounds.outHeight / sample > 512) sample *= 2
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    assets.open(match.image.path).use { BitmapFactory.decodeStream(it, null, options) }
                        ?.also { cache.put(match.image.path, it) }
                }
                main.post {
                    val current = target.get()
                    if (!closed && current?.tag === identity) {
                        current.setImageBitmap(bitmap)
                        current.contentDescription = "${entry.name}${if (!match.exact) "; base figure artwork" else ""}"
                        badge.text = if (match.exact) "" else "Base figure art"
                        badge.visibility = if (match.exact) View.GONE else View.VISIBLE
                    }
                }
            } catch (_: Exception) { /* Named fallback card remains usable without art. */ }
        }
    }

    fun close() { closed = true; worker.shutdown(); cache.evictAll() }
}

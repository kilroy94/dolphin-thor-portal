// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.secondscreen

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.dolphinemu.dolphinemu.NativeLibrary
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/** Same-process display host. Uses the upper Activity's existing controller/menu policy. */
object ScreenTest {
    private var owner = WeakReference<Activity>(null)
    var lower = WeakReference<ScreenTestActivity>(null)
    private val lines = ArrayDeque<String>()
    private val writer = Executors.newSingleThreadExecutor()
    private var started = SystemClock.elapsedRealtime()
    private var lastMotion = 0L
    var keys = 0L
        private set
    var motions = 0L
        private set
    var surface = false
    private var manager: DisplayManager? = null
    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) { record("display added id=$id"); describe() }
        override fun onDisplayChanged(id: Int) { record("display changed id=$id"); describe() }
        override fun onDisplayRemoved(id: Int) {
            record("display removed id=$id")
            if (lower.get()?.intent?.getIntExtra("display", -1) == id)
                lower.get()?.finishAndRemoveTask()
        }
    }

    @Suppress("DEPRECATION")
    fun displayId(activity: Activity) = activity.windowManager.defaultDisplay.displayId

    fun attach(activity: Activity) {
        owner.get()?.let { if (it !== activity) detach(it) }
        owner = WeakReference(activity)
        synchronized(this) {
            lines.clear()
            started = SystemClock.elapsedRealtime()
        }
        keys = 0; motions = 0
        manager = activity.getSystemService(DisplayManager::class.java)
        manager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        record("session SDK=${Build.VERSION.SDK_INT} upper display=${displayId(activity)}")
        describe()
    }

    fun hasOwner() = owner.get()?.let { !it.isFinishing && !it.isDestroyed } == true

    fun forwardControllerKey(event: KeyEvent): Boolean {
        // Some pads report their D-pad or buttons as keyboard events. Also inspect
        // the device sources so those presses and releases follow the same route.
        val sources = event.source or (event.device?.sources ?: 0)
        if (sources and InputDevice.SOURCE_GAMEPAD != InputDevice.SOURCE_GAMEPAD &&
            sources and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        owner.get()?.takeUnless { it.isFinishing || it.isDestroyed }?.dispatchKeyEvent(event)
        // Consume even an unmapped button: it must never activate a portal control.
        return true
    }

    fun forwardControllerMotion(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
        owner.get()?.takeUnless { it.isFinishing || it.isDestroyed }?.dispatchGenericMotionEvent(event)
        return true
    }

    fun detach(activity: Activity) {
        if (owner.get() !== activity) return
        record("upper destroyed changingConfigurations=${activity.isChangingConfigurations}")
        save(activity)
        lower.get()?.finishAndRemoveTask()
        manager?.unregisterDisplayListener(listener)
        manager = null
        owner.clear()
    }

    private fun describe() {
        manager?.displays?.forEach {
            record("display id=${it.displayId} name=${it.name} flags=${it.flags} state=${it.state} mode=${it.mode}")
        }
    }

    fun choose(activity: Activity) {
        if (Build.VERSION.SDK_INT < 26) return
        if (lower.get() != null) {
            Toast.makeText(activity, "Close the lower test before choosing another display", Toast.LENGTH_LONG).show()
            return
        }
        val displays = manager?.displays?.filter { it.displayId != displayId(activity) && it.isValid }.orEmpty()
        describe()
        if (displays.isEmpty()) {
            Toast.makeText(activity, "No secondary display detected", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(activity).setTitle("Choose the lower display")
            .setItems(displays.map { "${it.name} (ID ${it.displayId})" }.toTypedArray()) { _, index ->
                val id = displays[index].displayId
                val intent = Intent(activity, ScreenTestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    .putExtra("display", id)
                if (Build.VERSION.SDK_INT >= 29 &&
                    !activity.getSystemService(ActivityManager::class.java)
                        .isActivityStartAllowedOnDisplay(activity, id, intent)) {
                    record("launch denied display=$id")
                    Toast.makeText(activity, "Android does not allow this display", Toast.LENGTH_LONG).show()
                } else {
                    try {
                        record("launch requested display=$id")
                        activity.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(id).toBundle())
                    } catch (e: RuntimeException) {
                        record("launch failed ${e.javaClass.simpleName}: ${e.message}")
                        Toast.makeText(activity, "Display launch failed; see diagnostics", Toast.LENGTH_LONG).show()
                    }
                }
            }.show()
    }

    @Synchronized fun record(message: String) {
        val line = "${SystemClock.elapsedRealtime() - started}ms $message"
        if (lines.size >= 8192) lines.removeFirst()
        lines.addLast(line)
        android.util.Log.i("DolphinThor", line)
    }

    fun key(event: KeyEvent, menu: Boolean) {
        if (!event.isFromSource(InputDevice.SOURCE_GAMEPAD) && !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return
        keys++
        record("upper key device=${event.deviceId} code=${event.keyCode} action=${event.action} repeat=${event.repeatCount} menu=$menu")
    }

    fun motion(event: MotionEvent, menu: Boolean) {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return
        motions++
        val now = SystemClock.elapsedRealtime()
        if (now - lastMotion < 250) return
        lastMotion = now
        record("upper joystick device=${event.deviceId} events=$motions x=${event.getAxisValue(MotionEvent.AXIS_X)} y=${event.getAxisValue(MotionEvent.AXIS_Y)} lt=${event.getAxisValue(MotionEvent.AXIS_LTRIGGER)} rt=${event.getAxisValue(MotionEvent.AXIS_RTRIGGER)} menu=$menu")
    }

    fun status(): String = "Core running: ${NativeLibrary.IsRunningAndUnpaused()} | Surface: $surface\nUpper keys: $keys | Axis events: $motions\nUpper focus: ${owner.get()?.hasWindowFocus()}"

    private fun logSnapshot() = synchronized(this) { lines.joinToString("\n") + "\n${status()}\n" }

    fun requestExport() {
        (owner.get() as? org.dolphinemu.dolphinemu.activities.EmulationActivity)?.exportDiagnosticLog()
    }

    fun export(context: Context, uri: Uri, complete: (String) -> Unit) {
        val app = context.applicationContext
        val snapshot = logSnapshot()
        writer.execute {
            val message = try {
                val output = app.contentResolver.openOutputStream(uri, "wt")
                    ?: throw java.io.IOException("Cannot open chosen destination")
                output.bufferedWriter().use { it.write(snapshot) }
                "Diagnostics exported"
            } catch (e: Exception) { "Diagnostic export failed: ${e.message}" }
            Handler(Looper.getMainLooper()).post { complete(message) }
        }
    }

    fun save(context: Context, complete: ((String) -> Unit)? = null): String {
        val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, "Diagnostics")
        val snapshot = logSnapshot()
        writer.execute {
            val message = try {
                if (!directory.isDirectory && !directory.mkdirs())
                    throw java.io.IOException("Cannot create diagnostics directory")
                File(directory, "screen-test.txt").writeText(snapshot)
                "Saved Diagnostics/screen-test.txt"
            } catch (e: Exception) {
                android.util.Log.e("DolphinThor", "Could not save diagnostics", e)
                "Save failed: ${e.message}"
            }
            Handler(Looper.getMainLooper()).post { complete?.invoke(message) }
        }
        return "Diagnostics/screen-test.txt"
    }
}

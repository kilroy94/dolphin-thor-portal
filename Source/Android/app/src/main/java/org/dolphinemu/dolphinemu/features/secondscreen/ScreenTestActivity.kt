// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.secondscreen

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Focusable display host; controller events go to the upper Activity. */
class ScreenTestActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private var previousState = ""
    private var portal: org.dolphinemu.dolphinemu.features.skylanders.PortalPanel? = null
    private val tick = object : Runnable {
        override fun run() {
            val current = ScreenTest.status()
            status.text = "Lower display ${ScreenTest.displayId(this@ScreenTestActivity)}\n$current"
            val state = current.substringBefore("\n")
            if (state != previousState) {
                ScreenTest.record("lower observed $state")
                previousState = state
            }
            if (!ScreenTest.hasOwner()) { finishAndRemoveTask(); return }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Each display needs a focused window to acknowledge non-touch input. A resumed
        // Activity with FLAG_NOT_FOCUSABLE can trigger a five-second no-focused-window ANR.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        super.onCreate(savedInstanceState)
        val expected = intent.getIntExtra("display", -1)
        ScreenTest.record("lower created requested=$expected actual=${ScreenTest.displayId(this)}")
        if (!ScreenTest.hasOwner() || ScreenTest.displayId(this) != expected) {
            finishAndRemoveTask(); return
        }
        ScreenTest.lower = java.lang.ref.WeakReference(this)
        fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(13, 20, 29))
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or
                    android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                portal?.imeVisibility(insets.isVisible(android.view.WindowInsets.Type.ime()))
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        val bar = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        root.addView(bar)
        val portalStatus = TextView(this).apply {
            text = "Connecting to portal..."; textSize = 13f; setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(8), dp(6)); setTypeface(null, android.graphics.Typeface.BOLD)
        }
        bar.addView(portalStatus, LinearLayout.LayoutParams(0, dp(42), 1f))
        val diagnostics = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = android.view.View.GONE }
        bar.addView(Button(this).apply {
            text = "More"; isAllCaps = false; isFocusable = false; textSize = 12f
            setOnClickListener {
                diagnostics.visibility = if (diagnostics.visibility == android.view.View.VISIBLE)
                android.view.View.GONE else android.view.View.VISIBLE
            }
        }, LinearLayout.LayoutParams(dp(76), dp(42)))
        root.addView(diagnostics)
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 11f; diagnostics.addView(this) }
        fun diagnosticButton(text: String, action: (Button) -> Unit) = Button(this).also {
            it.text = text; it.isAllCaps = false; it.isFocusable = false
            it.setOnClickListener { _ -> action(it) }; diagnostics.addView(it)
        }
        diagnosticButton("Save diagnostics in fork") { button -> ScreenTest.save(this) { button.text = it } }
        diagnosticButton("Export diagnostics to chosen location") { ScreenTest.requestExport() }
        diagnosticButton("Close lower portal") { finishAndRemoveTask() }
        val host = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        portal = org.dolphinemu.dolphinemu.features.skylanders.PortalPanel(this, host, portalStatus)
        setContentView(root)
        root.requestApplyInsets()
        if (Build.VERSION.SDK_INT >= 33)
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { lowerBack() }
    }

    private fun lowerBack() { if (portal?.back() != true) finishAndRemoveTask() }
    @Deprecated("Android back callback")
    override fun onBackPressed() { lowerBack() }

    override fun onResume() {
        super.onResume()
        ScreenTest.record("lower resume")
        if (::status.isInitialized && !isFinishing) handler.post(tick)
    }
    override fun onPause() { ScreenTest.record("lower pause"); handler.removeCallbacks(tick); ScreenTest.save(this); super.onPause() }
    override fun onStop() { ScreenTest.record("lower stop"); super.onStop() }
    override fun onDestroy() {
        ScreenTest.record("lower destroy")
        handler.removeCallbacks(tick)
        portal?.close()
        if (ScreenTest.lower.get() === this) ScreenTest.lower.clear()
        ScreenTest.save(this)
        super.onDestroy()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); ScreenTest.record("lower focus=$hasFocus") }
    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) { super.onTopResumedActivityChanged(isTopResumedActivity); ScreenTest.record("lower topResumed=$isTopResumedActivity") }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (ScreenTest.forwardControllerKey(event)) return true
        ScreenTest.record("lower received key code=${event.keyCode} action=${event.action} device=${event.deviceId}")
        return super.dispatchKeyEvent(event)
    }
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (ScreenTest.forwardControllerMotion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP)
            ScreenTest.record("lower touch action=${event.actionMasked} ${ScreenTest.status()}")
        return super.dispatchTouchEvent(event)
    }
}

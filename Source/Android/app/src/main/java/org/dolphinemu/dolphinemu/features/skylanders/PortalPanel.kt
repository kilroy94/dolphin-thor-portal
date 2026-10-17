// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import android.text.InputFilter
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager

/** Portal overview and two explicit save workflows with Android text input. */
class PortalPanel(private val activity: Activity, private val root: LinearLayout, private val message: TextView) {
    private val main = Handler(Looper.getMainLooper())
    private val artwork = PortalArtwork(activity)
    private var token = 0L
    private var sessionReady = false
    private var alive = true
    private var busy = false
    private var selected: PortalCommands.CollectionFigure? = null
    private var selectedEntry: CatalogueEntry? = null
    private var selectedSlot: PortalSlotState? = null
    private var destination: PortalSlotState? = null
    private var catalogue = emptyMap<Long, CatalogueEntry>()
    private var copies = emptyList<PortalCommands.CollectionFigure>()
    private var state = emptyArray<PortalSlotState>()
    private var filter = CatalogueFilter()
    private var mode = 0 // Existing saves / new save.
    private var screen = 0 // Portal / picker / name.
    private var portalPage = 0
    private var allSpots = false
    private var request = 0L
    private var fingerprint = ""
    private var saveName = ""
    private val buttons = ArrayList<View>()
    private val title = label(root, "", 15f).apply {
        visibility = View.GONE
        minHeight = dp(42); setTypeface(null, Typeface.BOLD)
        setPadding(dp(8), dp(6), dp(8), dp(6)); background = backdrop(panelColor)
    }
    private val actions = row(root)
    private val tabs = row(root)
    private val tools = row(root)
    private val searchRow = row(root).apply { visibility = View.GONE }
    private val filterOptions = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
    private val confirm = column(root).apply { visibility = View.GONE }
    private val body = FrameLayout(activity).also { root.addView(it, LinearLayout.LayoutParams(-1, 0, 1f)) }
    private val slots = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val browserGrid = RecyclerView(activity).apply { itemAnimator = null }
    private val cardAdapter = CardAdapter()
    private val namePage = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var browser = PortalCommands.BrowserResult(emptyList(), emptyList())
    private val searchTask = Runnable { requestFilter() }
    private val poll = object : Runnable {
        override fun run() {
            if (!alive) return
            if (PortalCommands.session == 0L) PortalCommands.begin()
            val current = PortalCommands.session
            if (token != current) {
                token = current; sessionReady = false; busy = false
                selectedSlot = null; destination = null
                clearConfirmation(); clear(slots); fingerprint = ""
                updateButtons()
            }
            if (token == 0L) {
                sessionReady = false
                message.text = "Waiting for a Wii game with portal emulation enabled..."
                updateButtons(); main.postDelayed(this, 750); return
            }
            val requestedToken = token
            PortalCommands.snapshot(requestedToken) { state ->
                if (!alive) return@snapshot
                val ready = state != null && requestedToken == PortalCommands.session
                if (state == null) PortalCommands.invalidate(requestedToken)
                if (ready && !sessionReady) message.text = "Portal ready"
                sessionReady = ready
                if (!ready) message.text = "Waiting for the current game session..."
                updateButtons()
                if (ready) renderSlots(state)
                main.postDelayed(this, 750)
            }
        }
    }

    init {
        browserGrid.layoutManager = GridLayoutManager(activity, 3)
        browserGrid.adapter = cardAdapter
        title.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                title.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        root.isFocusableInTouchMode = true
        root.setPadding(dp(8), dp(4), dp(8), dp(6)); root.setBackgroundColor(baseColor)
        showHome()
        body.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                if (screen == 0) drawPortal() else if (screen == 1) renderBrowser()
            }
        }
        PortalCommands.catalogue { values ->
            if (!alive) return@catalogue
            catalogue = values; fingerprint = ""
            if (screen == 0) drawPortal() else requestFilter()
        }
        refreshCollection(); main.post(poll)
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
    private fun backdrop(color: Int, selected: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(10).toFloat()
        setStroke(dp(if (selected) 3 else 1), if (selected) accentColor else Color.rgb(63, 78, 96))
    }
    private fun column(parent: LinearLayout) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; parent.addView(this)
    }
    private fun row(parent: LinearLayout) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; parent.addView(this)
    }
    private fun label(parent: LinearLayout, text: String, size: Float = 14f) = TextView(activity).apply {
        this.text = text; setTextColor(Color.WHITE); textSize = size
        setPadding(dp(4), dp(3), dp(4), dp(3)); parent.addView(this)
    }
    private fun button(parent: LinearLayout, text: String, needsSession: Boolean = false,
                       accent: Boolean = false, action: () -> Unit) = Button(activity).apply {
        this.text = text; textSize = 13f; isAllCaps = false; isFocusable = false
        minimumWidth = 0; minWidth = 0; minHeight = dp(46)
        setPadding(dp(8), dp(4), dp(8), dp(4)); setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(if (accent) Color.rgb(31, 110, 130) else panelColor)
        tag = needsSession; buttons.add(this)
        parent.addView(this, if (parent.orientation == LinearLayout.HORIZONTAL)
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
            else LinearLayout.LayoutParams(-1, -2))
        setOnClickListener { if (isEnabled && alive) action() }
        isEnabled = !busy && (!needsSession || sessionReady && token != 0L && token == PortalCommands.session)
        alpha = if (isEnabled) 1f else .4f
    }
    private fun clear(parent: ViewGroup) {
        buttons.removeAll { view ->
            var ancestor = view.parent
            while (ancestor != null && ancestor !== parent) ancestor = ancestor.parent
            ancestor === parent
        }
        parent.removeAllViews()
    }
    private fun clearConfirmation() { clear(confirm); confirm.visibility = View.GONE }
    private fun updateButtons() {
        buttons.forEach {
            it.isEnabled = !busy && (it.tag != true || sessionReady && token != 0L && token == PortalCommands.session)
            it.alpha = if (it.isEnabled) 1f else .4f
        }
    }
    private fun setBusy(value: Boolean) { busy = value; updateButtons() }
    private fun putPage(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
        body.removeAllViews()
        if (view is RecyclerView) { body.addView(view, FrameLayout.LayoutParams(-1, -1)); return }
        body.addView(ScrollView(activity).apply {
            isFillViewport = true; addView(view, ViewGroup.LayoutParams(-1, -2))
        }, FrameLayout.LayoutParams(-1, -1))
    }
    private fun entryFor(figure: PortalCommands.CollectionFigure) = catalogue[(figure.info.id.toLong() shl 16) or figure.info.variant.toLong()]
    private fun entryFor(slot: PortalSlotState) = catalogue[(slot.id.toLong() shl 16) or slot.variant.toLong()]
    private fun copyFor(slot: PortalSlotState) = copies.singleOrNull {
        it.info.code == 0 && it.info.serial == slot.serial && it.info.id == slot.id && it.info.variant == slot.variant
    }
    private fun savedName(slot: PortalSlotState) =
        (PortalCommands.placedFile(token, slot) ?: copyFor(slot)?.file)?.nameWithoutExtension
    private fun levelFor(copy: PortalCommands.CollectionFigure): Int = state.firstOrNull { slot ->
        slot.present && (PortalCommands.placedFile(token, slot) ?: copyFor(slot)?.file) == copy.file
    }?.level ?: copy.info.level
    private fun heading(entry: CatalogueEntry?, name: String, fileName: String? = null): String =
        listOfNotNull(fileName, name, CatalogueModel.types.getOrNull(entry?.type ?: -1) ?: "Unknown type",
            CatalogueModel.games.getOrNull(entry?.game ?: -1) ?: "Unknown game").joinToString(" | ")

    private fun showHome() {
        hideKeyboard()
        screen = 0; selected = null; selectedEntry = null
        actions.visibility = View.VISIBLE
        searchRow.visibility = View.GONE; tabs.visibility = View.GONE; tools.visibility = View.GONE
        filterOptions.visibility = View.GONE; clearConfirmation()
        title.text = selectedSlot?.let { slot -> heading(entryFor(slot), entryFor(slot)?.name ?: "Figure ${slot.id}/${slot.variant}",
            savedName(slot)) } ?: ""
        homeActions(); drawPortal(); putPage(slots)
    }
    private fun homeActions() {
        clear(actions)
        val target = selectedSlot?.takeIf { it.present }
        if (target != null) {
            button(actions, "Remove", true) {
                setBusy(true); message.text = "Saving and removing..."
                PortalCommands.remove(token, target) { finished(it) }
            }
            button(actions, "Replace", true, true) { openPicker(target) }
            button(actions, "Deselect") { selectedSlot = null; showHome() }
        } else {
            button(actions, "Add figure", true, true) { openPicker(null) }
            button(actions, if (allSpots) "Usual spots" else "All 16 spots") {
                allSpots = !allSpots; portalPage = 0; homeActions(); drawPortal()
            }
        }
    }
    private fun drawPortal() {
        if (screen != 0) return
        clear(slots)
        val occupied = state.filter { it.present }
        val available = if (allSpots) state.toList() else occupied + state.filterNot { it.present }.take(maxOf(0, 4 - occupied.size))
        val pages = maxOf(1, (available.size + 3) / 4)
        portalPage = portalPage.coerceIn(0, pages - 1)
        val shown = available.drop(portalPage * 4).take(4)
        if (shown.isEmpty()) label(slots, "Waiting for portal spots...")
        shown.chunked(2).forEach { pair ->
            val line = row(slots)
            pair.forEach { slot ->
                val entry = entryFor(slot)
                val name = if (slot.present) entry?.name ?: "Figure ${slot.id}/${slot.variant}" else "+ Add figure"
                tile(line, entry.takeIf { slot.present }, name,
                    if (slot.present) savedName(slot) ?: "On portal · spot ${slot.slot + 1}" else "Empty spot ${slot.slot + 1}",
                    selectedSlot?.instance == slot.instance && slot.present, portal = true, needsSession = true, level = slot.level) {
                    if (slot.present) { selectedSlot = slot; showHome() } else openPicker(null)
                }
            }
            if (pair.size == 1) line.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
        }
        if (pages > 1) {
            val navigation = row(slots)
            button(navigation, "Previous") { if (portalPage > 0) { portalPage--; drawPortal() } }
            label(navigation, "${portalPage + 1}/$pages", 12f)
            button(navigation, "Next") { if (portalPage + 1 < pages) { portalPage++; drawPortal() } }
        }
    }
    private fun renderSlots(value: Array<PortalSlotState>) {
        val current = value.joinToString { "${it.slot}:${it.present}:${it.instance}:${it.id}:${it.variant}:${it.level}" }
        state = value
        if (current == fingerprint) return
        fingerprint = current
        val prior = selectedSlot
        if (prior != null && value.none { it.present && it.slot == prior.slot && it.instance == prior.instance }) {
            selectedSlot = null
            if (screen == 0) showHome()
        }
        if (screen == 0) drawPortal()
        if (screen == 1 && filterOptions.visibility != View.VISIBLE) renderBrowser()
    }

    private fun openPicker(target: PortalSlotState?) {
        destination = target; selected = null; selectedEntry = null
        screen = 1; mode = 0; filter = CatalogueFilter()
        title.text = ""
        tabs.visibility = View.VISIBLE; tools.visibility = View.VISIBLE
        clear(tabs)
        button(tabs, "Existing saves", accent = true) { chooseMode(0) }
        button(tabs, "Create new save") { chooseMode(1) }
        pickerTools(); pickerActions(); requestFilter(); putPage(browserGrid); refreshCollection()
    }
    private fun chooseMode(value: Int) {
        screen = 1; mode = value; selected = null; selectedEntry = null
        hideKeyboard(); clearConfirmation()
        title.text = ""
        clear(tabs)
        button(tabs, "Existing saves", accent = value == 0) { chooseMode(0) }
        button(tabs, "Create new save", accent = value == 1) { chooseMode(1) }
        pickerActions(); requestFilter(); putPage(browserGrid)
    }
    private fun pickerTools() {
        clear(tools)
        tools.addView(ImageButton(activity).apply {
            setImageResource(android.R.drawable.ic_menu_search); contentDescription = "Search figures"
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            background = backdrop(panelColor); isFocusable = false; tag = false; buttons.add(this)
            setOnClickListener { if (isEnabled) showSearch() }
        }, LinearLayout.LayoutParams(dp(48), dp(46)).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
        button(tools, "Filters") { showFilters() }
        button(tools, "Reset") {
            filter = CatalogueFilter(); (searchRow.getChildAt(0) as? EditText)?.setText("")
            requestFilter()
        }
        button(tools, "Refresh") { refreshCollection() }
    }
    private fun pickerActions() {
        clear(actions)
        button(actions, "Back to portal") { selectedSlot = destination; showHome() }
        if (mode == 0 && selected != null) button(actions, if (destination == null) "Place saved figure" else "Replace with this save", true, true) { insertSelected() }
        else if (mode == 1 && selectedEntry != null) button(actions, "Name & create", true, true) { showNamePage() }
    }
    private fun selectCopy(figure: PortalCommands.CollectionFigure) {
        hideKeyboard()
        selected = figure; selectedEntry = entryFor(figure)
        title.text = heading(selectedEntry, figure.info.name, figure.file.nameWithoutExtension)
        pickerActions(); renderBrowser()
    }
    private fun selectEntry(entry: CatalogueEntry) {
        hideKeyboard()
        selected = null; selectedEntry = entry; title.text = heading(entry, entry.name)
        pickerActions(); renderBrowser()
    }
    private fun requestFilter() {
        if (!alive) return
        val version = ++request
        PortalCommands.filter(copies, filter, false) { value ->
            if (!alive || version != request) return@filter
            browser = value
            if (screen == 1) renderBrowser()
        }
    }
    private fun refreshCollection() {
        PortalCommands.collection(activity) { figures, index, error ->
            if (!alive) return@collection
            copies = figures
            if (error != null) message.text = "Collection error: $error"
            if (screen == 0) showHome() else requestFilter()
        }
    }
    private fun renderBrowser() {
        if (screen != 1) return
        val columns = maxOf(2, ((body.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels) / dp(125))).coerceAtMost(5)
        (browserGrid.layoutManager as GridLayoutManager).spanCount = columns
        cardAdapter.items = if (mode == 1) browser.entries else browser.copies
        cardAdapter.notifyDataSetChanged()
    }
    private inner class CardAdapter : RecyclerView.Adapter<CardHolder>() {
        var items: List<Any> = emptyList()
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): CardHolder {
            val holder = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = RecyclerView.LayoutParams(-1, -2)
            }
            return CardHolder(holder)
        }
        override fun onBindViewHolder(holder: CardHolder, position: Int) {
            clear(holder.container)
            val item = items[position]
            if (item is CatalogueEntry) {
                tile(holder.container, item, item.name, CatalogueModel.types.getOrNull(item.type) ?: "Unknown type",
                    selectedEntry?.key == item.key, portal = false,
                    level = copies.filter { entryFor(it)?.key == item.key }.maxOfOrNull { levelFor(it) } ?: 0) { selectEntry(item) }
            } else {
                val copy = item as PortalCommands.CollectionFigure
                tile(holder.container, entryFor(copy), copy.file.nameWithoutExtension,
                    if (copy.info.code == 0) copy.info.name else "Unreadable save", selected?.file == copy.file,
                    portal = false, level = levelFor(copy)) { selectCopy(copy) }
            }
        }
        override fun onViewRecycled(holder: CardHolder) { clear(holder.container) }
    }
    private class CardHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container)
    private fun tile(parent: LinearLayout, entry: CatalogueEntry?, name: String, subtitle: String,
                     selected: Boolean, portal: Boolean, needsSession: Boolean = false, level: Int = 0, action: () -> Unit): LinearLayout {
        val tile = LinearLayout(activity).apply {
            orientation = if (portal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(6), dp(6), dp(6))
            background = backdrop(panelColor, selected); isFocusable = false
            tag = needsSession; buttons.add(this)
            parent.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            setOnClickListener { if (isEnabled && alive) action() }; contentDescription = "$name, $subtitle"
            isEnabled = !busy && (!needsSession || sessionReady && token != 0L && token == PortalCommands.session)
            alpha = if (isEnabled) 1f else .4f
        }
        val image = ImageView(activity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val portalHeight = ((body.height - dp(44)) / 2 - dp(12)).coerceIn(dp(64), dp(100))
        val artFrame = FrameLayout(activity)
        tile.addView(artFrame, LinearLayout.LayoutParams(if (portal) dp(72) else -1, if (portal) portalHeight else dp(145)))
        artFrame.addView(image, FrameLayout.LayoutParams(-1, -1))
        if (level in 1..20) artFrame.addView(TextView(activity).apply {
            text = level.toString(); textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD); background = backdrop(Color.rgb(24, 104, 118))
            contentDescription = "Saved level $level"
        }, FrameLayout.LayoutParams(dp(28), dp(26), Gravity.TOP or Gravity.END))
        val captions = if (portal) column(tile) else tile
        if (portal) captions.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        label(captions, name, 14f).apply {
            setTypeface(null, Typeface.BOLD); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        label(captions, subtitle, 11f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE }
        val badge = label(captions, "", 10f)
        if (entry != null) artwork.show(image, entry, badge) else badge.visibility = View.GONE
        return tile
    }

    private fun showNamePage() {
        val entry = selectedEntry ?: return
        hideKeyboard()
        screen = 2; saveName = entry.name
        searchRow.visibility = View.GONE; actions.visibility = View.VISIBLE
        tabs.visibility = View.GONE; tools.visibility = View.GONE; filterOptions.visibility = View.GONE
        clear(namePage); title.text = heading(entry, entry.name)
        label(namePage, "NAME YOUR NEW SAVE", 18f)
        label(namePage, "This creates a separate save with new progress. Your other saves are preserved.")
        editor(namePage, "Save name", saveName, 80) { saveName = it }
        label(namePage, "Names already in use get a number: Spyro.sky, Spyro 2.sky, Spyro 3.sky.", 12f)
        clear(actions)
        button(actions, "Back to cards") {
            hideKeyboard(); screen = 1; tabs.visibility = View.VISIBLE; tools.visibility = View.VISIBLE
            pickerActions(); putPage(browserGrid)
        }
        button(actions, if (destination == null) "Create & place" else "Create & replace", true, true) { hideKeyboard(); createAndPlace(entry) }
        putPage(namePage)
    }
    private fun insertSelected() {
        val figure = selected ?: return
        if (figure.info.code != 0) { message.text = PortalCommandResult(figure.info.code, -1, 0).message(); return }
        setBusy(true); message.text = "Placing ${figure.file.nameWithoutExtension}..."
        PortalCommands.insert(activity, token, figure.file, destination) { finished(it) }
    }
    private fun createAndPlace(entry: CatalogueEntry) {
        setBusy(true); message.text = "Creating ${FigureSaveNames.stem(saveName, entry.name)}..."
        PortalCommands.create(activity, token, entry, saveName) { created, text ->
            if (!alive) return@create
            message.text = text
            if (created == null) { setBusy(false); return@create }
            selected = created
            PortalCommands.insert(activity, token, created.file, destination) { result ->
                if (result.code != 0) message.text = "$text. ${result.message()} The new save is in Existing saves."
                finished(result, preserveError = true)
                if (result.code != 0) offerCreatedSave(created)
            }
        }
    }
    private fun finished(result: PortalCommandResult, preserveError: Boolean = false) {
        if (!alive) return
        setBusy(false); clearConfirmation(); fingerprint = ""
        if (result.code == 0) {
            selectedSlot = null; destination = null; message.text = "Portal updated"
            showHome(); refreshCollection()
        } else if (!preserveError) message.text = result.message()
    }

    private fun offerCreatedSave(copy: PortalCommands.CollectionFigure) {
        chooseMode(0); selectCopy(copy); refreshCollection()
    }

    private fun editor(parent: LinearLayout, hint: String, initial: String, limit: Int,
                       changed: (String) -> Unit): EditText = EditText(activity).apply {
        this.hint = hint; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); textSize = 17f
        setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        filters = arrayOf(InputFilter.LengthFilter(limit)); setText(initial)
        setPadding(dp(10), dp(8), dp(10), dp(8)); minHeight = dp(48)
        background = backdrop(panelColor)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(); true
            } else false
        }
    }
    private fun showSearch() {
        filterOptions.visibility = View.GONE; clear(searchRow); searchRow.visibility = View.VISIBLE
        val input = editor(searchRow, "Search figures or saved filenames", filter.query, 64) {
            filter = filter.copy(query = it); ++request
            main.removeCallbacks(searchTask); main.postDelayed(searchTask, 120)
        }
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        input.requestFocus(); input.setSelection(input.text.length)
        input.post { if (alive && input.hasFocus()) activity.getSystemService(InputMethodManager::class.java)
            .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
    }
    private fun hideKeyboard() {
        activity.currentFocus?.let {
            activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
        root.requestFocus()
        actions.visibility = View.VISIBLE
        tabs.visibility = if (screen == 1) View.VISIBLE else View.GONE
        tools.visibility = if (screen == 1) View.VISIBLE else View.GONE
    }
    fun imeVisibility(shown: Boolean) {
        if (screen == 1) {
            tabs.visibility = if (shown) View.GONE else View.VISIBLE
            tools.visibility = if (shown) View.GONE else View.VISIBLE
            actions.visibility = if (shown) View.GONE else View.VISIBLE
        }
    }
    private fun closeFilters() {
        filterOptions.visibility = View.GONE
        hideKeyboard(); pickerActions(); putPage(browserGrid)
        title.visibility = if (title.text.isEmpty()) View.GONE else View.VISIBLE
    }
    private fun showFilters() {
        hideKeyboard(); tabs.visibility = View.GONE; tools.visibility = View.GONE; title.visibility = View.GONE
        clear(filterOptions); filterOptions.visibility = View.VISIBLE
        clear(actions); button(actions, "Done", accent = true) { closeFilters() }
        fun section(name: String, names: List<String>, indexes: List<Int>, selected: Set<Int>, changed: (Set<Int>) -> Unit) {
            val heading = button(filterOptions, "$name  ▸") {}
            val choices = column(filterOptions).apply { visibility = View.GONE }
            var current = selected
            heading.setOnClickListener {
                choices.visibility = if (choices.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                heading.text = "$name  ${if (choices.visibility == View.VISIBLE) "▾" else "▸"}"
            }
            fun refreshChecks() {
                for (i in 0 until choices.childCount) {
                    val check = choices.getChildAt(i) as? CheckBox ?: continue
                    check.isChecked = if (i == 0) current.size == indexes.size else indexes[i - 1] in current
                }
            }
            var updating = false
            (listOf("All") + indexes.map { names[it] }).forEachIndexed { position, label ->
                choices.addView(CheckBox(activity).apply {
                    text = label; setTextColor(Color.WHITE); isFocusable = false
                    isChecked = if (position == 0) current.size == indexes.size else indexes[position - 1] in current
                    setOnCheckedChangeListener { _, checked ->
                        if (!updating) {
                            current = if (position == 0) { if (checked) indexes.toSet() else emptySet() }
                                else if (checked) current + indexes[position - 1] else current - indexes[position - 1]
                            updating = true; refreshChecks(); updating = false
                            changed(current); requestFilter()
                        }
                    }
                })
            }
        }
        section("Game categories", CatalogueModel.games, (0..4).toList(), filter.games) { filter = filter.copy(games = it) }
        section("Type", CatalogueModel.types, (1..10).toList(), filter.selectedTypes) { filter = filter.copy(selectedTypes = it) }
        section("Element", CatalogueModel.elements, (1..11).toList(), filter.selectedElements) { filter = filter.copy(selectedElements = it) }
        label(filterOptions, "SORT BY", 12f)
        val sort = android.widget.RadioGroup(activity)
        filterOptions.addView(sort)
        listOf("Element", "Alphabetical").forEachIndexed { index, name ->
            sort.addView(android.widget.RadioButton(activity).apply {
                id = View.generateViewId(); text = name; setTextColor(Color.WHITE); isFocusable = false
                isChecked = if (index == 0) filter.sortByElement else !filter.sortByElement
                setOnCheckedChangeListener { _, checked ->
                    if (checked) { filter = filter.copy(sortByElement = index == 0); requestFilter() }
                }
            })
        }
        putPage(filterOptions)
    }
    fun back(): Boolean {
        if (activity.currentFocus is EditText) { hideKeyboard(); return true }
        if (filterOptions.visibility == View.VISIBLE) { closeFilters(); return true }
        if (screen != 0) { showHome(); return true }
        return false
    }
    fun close() { alive = false; hideKeyboard(); main.removeCallbacks(poll); main.removeCallbacks(searchTask); artwork.close() }

    companion object {
        private val baseColor = Color.rgb(13, 20, 29)
        private val panelColor = Color.rgb(29, 43, 59)
        private val accentColor = Color.rgb(86, 220, 217)
    }
}

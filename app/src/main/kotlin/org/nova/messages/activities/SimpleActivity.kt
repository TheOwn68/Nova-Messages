package org.nova.messages.activities

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import android.graphics.Bitmap
import android.graphics.RectF
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest
import com.bumptech.glide.load.engine.DiskCacheStrategy
import androidx.appcompat.widget.ListPopupWindow
import androidx.appcompat.widget.Toolbar
import androidx.palette.graphics.Palette
import androidx.core.view.get
import androidx.core.view.size
import androidx.core.graphics.toColorInt
import androidx.core.view.updateLayoutParams
import com.google.android.material.appbar.AppBarLayout
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.nova.messages.R
import org.nova.messages.extensions.config
import org.nova.messages.helpers.*
import java.io.File
import kotlin.math.max

open class SimpleActivity : BaseSimpleActivity() {

    val uiScale get() = config.uiScale



    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        // Removed to fix infinite layout loop and jitter
    }

    private val cabHideObserver = ViewTreeObserver.OnGlobalLayoutListener {
        if (currentActionMode != null) {
            hideSystemSelectionBar()
        }
    }

    fun getScaledTextSize(multiplier: Float = 1.0f): Float {
        return getTextSize() * multiplier * uiScale
    }

    fun getScaledDimen(dimenId: Int): Int {
        return (resources.getDimension(dimenId) * uiScale).toInt()
    }

    fun Int.getScaledPx(): Int {
        return (this * resources.displayMetrics.density * uiScale).toInt()
    }

    fun setupScaledToolbar(toolbar: Toolbar) {
        val baseHeight = 70.getScaledPx()
        val minHeightRequired = (48 * resources.displayMetrics.density).toInt()
        val finalHeight = max(baseHeight, minHeightRequired)
        
        toolbar.updateLayoutParams {
            height = finalHeight
        }
    }

    fun getRingtoneTitle(uriString: String): String {
        return if (uriString.isEmpty()) {
            "Default"
        } else {
            try {
                RingtoneManager.getRingtone(this, Uri.parse(uriString))?.getTitle(this) ?: "Unknown"
            } catch (_: Exception) {
                "Unknown"
            }
        }
    }

    fun getCustomTypeface(): Typeface {
        return when (config.fontFamilyNova) {
            1 -> Typeface.MONOSPACE
            2 -> Typeface.SERIF
            3 -> Typeface.SANS_SERIF
            else -> org.fossify.commons.helpers.FontHelper.getTypeface(this, config.fontFamilyNova, "")
        }
    }

    fun updateAppFonts(view: View?) {
        if (view == null) return
        val customTypeface = getCustomTypeface()
        if (view is TextView) {
            val style = view.typeface?.style ?: android.graphics.Typeface.NORMAL
            view.typeface = android.graphics.Typeface.create(customTypeface, style)
            
            // Apply custom text color if not in toolbar AND not a message bubble/list item
            val id = view.id
            val excludedIds = listOf(
                R.id.thread_toolbar_title,
                R.id.nova_title,
                R.id.settings_toolbar_title,
                R.id.new_conversation_toolbar_title,
                R.id.conversation_details_toolbar_title,
                R.id.thread_message_body,
                R.id.nova_search_input,
                R.id.new_conversation_address,
                R.id.thread_type_message,
                R.id.thread_sim_number,
                R.id.thread_message_carrier_warning,
                R.id.conversations_fab,
                R.id.nav_home_icon,
                R.id.nav_settings_icon,
                R.id.nova_search_icon
            )
                             
            if (!excludedIds.contains(id)) {
                val color = config.mainTextColor
                if (color != 0) {
                    view.setTextColor(color)
                }
                
                // Apply scaled font size
                view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
            }
        }
        (view as? ViewGroup)?.let {
            for (i in 0 until it.childCount) {
                updateAppFonts(it.getChildAt(i))
            }
        }
    }

    fun applyCustomColors() {
        val density = resources.displayMetrics.density

        // 1. Force main background to apply to the window decor view
        if (config.mainBgMode == BG_MODE_IMAGE && config.mainBackgroundImage.isNotEmpty()) {
            loadBackgroundImage(config.mainBackgroundImage, window.decorView, config.mainBgCropRect)
        } else {
            clearGlideTarget(window.decorView)
            window.decorView.setBackgroundColor(config.mainBackgroundColor)
        }
        
        // 2. Apply top bar color (HARD RECURSIVE SHAPE GUARD)
        val appBar = findViewById<AppBarLayout>(R.id.settings_appbar) ?: 
                     findViewById<AppBarLayout>(R.id.thread_appbar) ?: 
                     findViewById<AppBarLayout>(R.id.main_appbar) ?:
                     findViewById<AppBarLayout>(R.id.new_conversation_appbar) ?:
                     findViewById<AppBarLayout>(R.id.conversation_details_appbar) ?:
                     findViewById<AppBarLayout>(R.id.archive_appbar) ?:
                     findViewById<AppBarLayout>(R.id.recycle_bin_appbar) ?:
                     findViewById<AppBarLayout>(R.id.block_keywords_appbar) ?:
                     findViewById<AppBarLayout>(R.id.vcard_appbar)
                     
        val toolbar = findViewById<Toolbar>(R.id.settings_toolbar) ?: 
                      findViewById<Toolbar>(R.id.thread_toolbar) ?: 
                      findViewById<Toolbar>(R.id.main_toolbar) ?:
                      findViewById<Toolbar>(R.id.new_conversation_toolbar) ?:
                      findViewById<Toolbar>(R.id.conversation_details_toolbar)
        
        if (appBar != null) {
            val barColor = if (config.topBarColor != 0) config.topBarColor else Color.BLACK
            val barRadius = 26 * density * uiScale
            val useNewUi = config.useNewUi
            
            val topBarImage = config.topBarImage
            val barShape = if (useNewUi) {
                // Modern Multi-Tone Gradient (Matching Contact Pills)
                val lightened = adjustColor(barColor, 1.2f)
                val darkened = adjustColor(barColor, 0.8f)
                GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lightened, barColor, darkened)).apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, barRadius, barRadius, barRadius, barRadius)
                }
            } else {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, barRadius, barRadius, barRadius, barRadius)
                    setColor(barColor)
                }
            }

            if (config.topBarBgMode == BG_MODE_IMAGE && topBarImage.isNotEmpty()) {
                loadBackgroundImage(topBarImage, appBar, config.topBarCropRect)
            } else {
                clearGlideTarget(appBar)
                appBar.background = barShape
            }
            
            appBar.isLiftOnScroll = false
            appBar.stateListAnimator = null
            
            // Material 3 Depth & Clipping (Rounded only on bottom)
            appBar.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    // Start rect ABOVE view so top corners are hidden/flat
                    outline.setRoundRect(0, -barRadius.toInt(), view.width, view.height, barRadius)
                }
            }
            appBar.clipToOutline = true

            if (useNewUi) {
                appBar.background = appBar.background ?: ColorDrawable(barColor) // Ensure background is set if Glide is async
                appBar.elevation = 14 * density // Extra Stronger shadow
                appBar.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                // Ensure shadow is visible by disabling clipping on parent
                (appBar.parent as? ViewGroup)?.let {
                    it.clipChildren = false
                    it.clipToPadding = false
                }
            } else {
                appBar.elevation = 0f
            }
            
            // Force title tinting for main screen icons if they exist
            val topTextColor = config.topBarTextColor
            findViewById<View>(R.id.nova_nav_container)?.let {
                val inputBarTextColor = config.inputBarTextColor
                findViewById<ImageView>(R.id.nav_home_icon)?.imageTintList = ColorStateList.valueOf(inputBarTextColor)
                findViewById<ImageView>(R.id.nav_settings_icon)?.imageTintList = ColorStateList.valueOf(inputBarTextColor)
                findViewById<ImageView>(R.id.nova_search_icon)?.imageTintList = ColorStateList.valueOf(inputBarTextColor)
                findViewById<View>(R.id.nav_divider1)?.setBackgroundColor(inputBarTextColor.withAlpha(0.2f))
                findViewById<View>(R.id.nav_divider2)?.setBackgroundColor(inputBarTextColor.withAlpha(0.2f))
            }

            findViewById<TextView>(R.id.conversations_fab)?.let {
                it.visibility = View.VISIBLE
                it.setTextColor(topTextColor)
                if (useNewUi) {
                    it.alpha = 0.4f
                    it.elevation = 20 * density
                    it.translationZ = 10 * density
                    it.bringToFront()
                } else {
                    it.alpha = 1.0f
                    it.elevation = 0f
                    it.translationZ = 0f
                }
            }
        }
        
        toolbar?.setBackgroundColor(Color.TRANSPARENT)
        
        // 3. Apply top bar text color
        if (toolbar != null) {
            val topBarColor = config.topBarTextColor
            toolbar.setTitleTextColor(topBarColor)
            
            // Use specific MaterialToolbar method for navigation icon
            if (toolbar is com.google.android.material.appbar.MaterialToolbar) {
                toolbar.setNavigationIconTint(topBarColor)
            }
            toolbar.navigationIcon?.setColorFilter(topBarColor, android.graphics.PorterDuff.Mode.SRC_IN)
            
            toolbar.overflowIcon?.setColorFilter(topBarColor, android.graphics.PorterDuff.Mode.SRC_IN)
            
            // Also tint menu items if they exist
            for (i in 0 until toolbar.menu.size) {
                val item = toolbar.menu[i]
                item.icon?.setColorFilter(topBarColor, android.graphics.PorterDuff.Mode.SRC_IN)
            }
        }
        
        val titleText = findViewById<TextView>(R.id.thread_toolbar_title) ?: 
                         findViewById<TextView>(R.id.nova_title) ?: 
                         findViewById<TextView>(R.id.settings_toolbar_title) ?:
                         findViewById<TextView>(R.id.conversation_details_toolbar_title)
        titleText?.setTextColor(config.topBarTextColor)
        titleText?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.75f))
        
        // 4. Apply input bar colors (Maintaining Rounded Shape)
        val inputBar = findViewById<View>(R.id.nova_nav_container) ?: 
                       findViewById<View>(R.id.nova_message_input_bar) ?:
                       findViewById<View>(R.id.new_conversation_search_container) ?:
                       findViewById<View>(R.id.classic_search_container)
        if (inputBar != null) {
            val inputBgColor = config.inputBarBackgroundColor
            val inputRadius = 28 * density * uiScale
            val inputBarImage = config.inputBarImage
            
            if (config.inputBarBgMode == BG_MODE_IMAGE && inputBarImage.isNotEmpty()) {
                loadBackgroundImage(inputBarImage, inputBar, config.inputBarCropRect) {
                    inputBar.outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, inputRadius)
                        }
                    }
                    inputBar.clipToOutline = true
                }
            } else {
                clearGlideTarget(inputBar)
                inputBar.clipToOutline = false
                val inputShape = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = inputRadius
                    setColor(inputBgColor)
                }
                inputBar.background = inputShape
            }
        }
        
        // 5. Targeted EditText coloring for live typed text
        val inputEditTexts = listOfNotNull(
            findViewById<EditText>(R.id.nova_search_input),
            findViewById<EditText>(R.id.new_conversation_address),
            findViewById<EditText>(R.id.thread_type_message),
            findViewById<EditText>(R.id.classic_search_input),
        )
        
        val textColorCSL = ColorStateList.valueOf(config.inputBarTextColor)
        val searchIcons = listOfNotNull(
            findViewById<ImageView>(R.id.nova_search_icon),
            findViewById<ImageView>(R.id.new_conversation_search_icon),
            findViewById<ImageView>(R.id.classic_settings_icon)
        )
        
        for (icon in searchIcons) {
            icon.imageTintList = textColorCSL
            icon.alpha = 0.7f
        }

        for (et in inputEditTexts) {
            et.setTextColor(textColorCSL)
            val hintColor = if (config.inputBarTextColor == Color.WHITE) {
                "#888888".toColorInt()
            } else {
                config.inputBarTextColor.withAlpha(0.6f)
            }
            et.setHintTextColor(hintColor)
            
            // Add a TextWatcher to force color on every keystroke
            if (et.tag != "color_watcher_attached") {
                et.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        et.setTextColor(config.inputBarTextColor)
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
                et.tag = "color_watcher_attached"
            }
        }
        
        // Apply gear icon and plus button colors (Force 100% Sync with Top Bar Text, 70% Transparent)
        val topTextColor = config.topBarTextColor
        findViewById<TextView>(R.id.conversations_fab)?.let {
            it.setTextColor(topTextColor)
            it.alpha = 0.3f // 70% transparent = 0.3 opacity
            if (config.useNewUi) {
                it.elevation = 12 * density * uiScale
                it.bringToFront()
            }
        }
        
        // Force settings labels to follow main text color
        val mainTextCol = config.mainTextColor
        val settingsLabels = listOf(
            R.id.settings_customization_label,
            R.id.settings_top_bar_label,
            R.id.settings_main_bg_label,
            R.id.settings_input_bar_label,
            R.id.settings_bubble_customization_label,
            R.id.settings_general_label,
            R.id.settings_ui_scale_label,
            R.id.settings_font_size_label,
            R.id.settings_font_label,
            R.id.settings_reset_defaults,
            R.id.settings_font_size,
            R.id.settings_font,
            R.id.settings_top_bar_text_color_label,
            R.id.settings_background_color_label,
            R.id.settings_input_bar_text_color_label,
            R.id.settings_sent_bubble_color_label,
            R.id.settings_sent_bubble_text_color_label,
            R.id.settings_received_bubble_color_label,
            R.id.settings_received_bubble_text_color_label
        )
        
        settingsLabels.forEach { labelId ->
            findViewById<TextView>(labelId)?.setTextColor(mainTextCol)
        }

        findViewById<ImageView>(R.id.thread_add_attachment)?.let {
            it.imageTintList = ColorStateList.valueOf(config.inputBarTextColor)
            it.alpha = 1.0f
        }

        val actionButtons = listOfNotNull(
            findViewById<View>(R.id.thread_send_message),
            findViewById<View>(R.id.new_conversation_confirm)
        )

        for (view in actionButtons) {
            val color = config.inputBarTextColor
            if (view is TextView) {
                view.setTextColor(color)
                view.compoundDrawables.forEach { it?.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN) }
                view.compoundDrawablesRelative.forEach { it?.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN) }
            } else if (view is ImageView) {
                view.imageTintList = ColorStateList.valueOf(color)
                view.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
            }
        }
    }

    protected fun adjustColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = Math.round(Color.red(color) * factor).coerceIn(0, 255)
        val g = Math.round(Color.green(color) * factor).coerceIn(0, 255)
        val b = Math.round(Color.blue(color) * factor).coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    private fun forceTransparentContainers(view: View) {
        val id = view.id
        if (id == R.id.main_coordinator || 
            id == R.id.settings_coordinator || 
            id == R.id.thread_coordinator ||
            id == R.id.main_nested_scrollview || 
            id == R.id.main_coordinator_wrapper ||
            id == R.id.message_holder ||
            id == R.id.attachment_picker_holder) {
            view.setBackgroundColor(Color.TRANSPARENT)
        }
        
        // Ensure lists are always visible and not accidentally hidden
        if (view is androidx.recyclerview.widget.RecyclerView) {
            view.visibility = View.VISIBLE
        }
        
        (view as? ViewGroup)?.let {
            for (i in 0 until it.childCount) {
                forceTransparentContainers(it.getChildAt(i))
            }
        }
    }

    fun Int.withAlpha(alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (this and 0x00FFFFFF) or (a shl 24)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
    }

    private var selectionCancelCallback: (() -> Unit)? = null
    private val selectionBackCallback = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            selectionCancelCallback?.invoke()
        }
    }

    private var isCheckingPackage = false

    override fun getPackageName(): String {
        return if (isCheckingPackage) "org.fossify.messages" else super.getPackageName()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isCheckingPackage = true
        super.onCreate(savedInstanceState)
        isCheckingPackage = false

        onBackPressedDispatcher.addCallback(this, selectionBackCallback)
        requestHighRefreshRate()
        applyCustomColors()
    }

    override fun onResume() {
        super.onResume()
        if (config.appLockType != LOCK_NONE && !AppLockActivity.isUnlocked && this !is AppLockActivity) {
            startActivity(Intent(this, AppLockActivity::class.java))
        }
        updateAppFonts(findViewById(android.R.id.content))
        applyCustomColors()
        
        // Suppress popups safely
        config.appSideloadingStatus = 0
        config.hadThankYouInstalled = true
        config.appRunCount = 1
    }

    override fun onSupportActionModeStarted(mode: androidx.appcompat.view.ActionMode) {
        super.onSupportActionModeStarted(mode)
        currentActionMode = mode
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener(cabHideObserver)
        // Aggressively hide the system CAB to avoid duplicate UI
        hideSystemSelectionBar()
        
        // Final Force
        mode.customView = View(this).apply { layoutParams = ViewGroup.LayoutParams(0, 0) }
        mode.title = ""
        mode.subtitle = ""
    }

    private var currentActionMode: androidx.appcompat.view.ActionMode? = null

    private fun hideSystemSelectionBar() {
        try {
            val hideBar = { view: View ->
                view.visibility = View.GONE
                view.alpha = 0f
                view.isClickable = false
                view.isFocusable = false
                if (view.layoutParams != null) {
                    view.layoutParams.height = 0
                }
            }

            // Layer 1: Traditional ID lookup
            window.decorView.findViewById<View>(androidx.appcompat.R.id.action_mode_bar)?.let { hideBar(it) }
            window.decorView.findViewById<View>(android.R.id.custom)?.parent?.let { 
                if (it is View && it.javaClass.simpleName.contains("ActionMode")) hideBar(it) 
            }
            
            // Layer 2: Recursive class-based lookup
            findAndHideActionModeView(window.decorView)
        } catch (_: Exception) {}
    }

    private fun findAndHideActionModeView(view: View) {
        val name = view.javaClass.simpleName
        if (name.contains("ActionMode") || name.contains("ActionBarContextView")) {
            view.visibility = View.GONE
            view.alpha = 0f
            if (view.layoutParams != null) {
                view.layoutParams.height = 0
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findAndHideActionModeView(view.getChildAt(i))
            }
        }
    }

    override fun onSupportActionModeFinished(mode: androidx.appcompat.view.ActionMode) {
        super.onSupportActionModeFinished(mode)
        currentActionMode = null
        selectionCancelCallback = null
        window.decorView.viewTreeObserver.removeOnGlobalLayoutListener(cabHideObserver)
    }

    fun isCustomSelectionBarVisible(): Boolean {
        val barContainer = findViewById<View>(R.id.selection_bar) ?: 
                           findViewById<View>(R.id.selection_bar_container)
        return barContainer?.visibility == View.VISIBLE
    }

    fun toggleCustomSelectionBar(show: Boolean, count: Int = 0, actions: List<Int> = emptyList(), senderInfo: String? = null, onAction: (Int) -> Unit = {}) {
        android.util.Log.d("SelectionBar", "toggleCustomSelectionBar: show=$show, count=$count")
        
        selectionBackCallback.isEnabled = show
        if (show) {
            selectionCancelCallback = { 
                onAction(R.id.selection_cancel)
                currentActionMode?.finish()
            }
        } else {
            selectionCancelCallback = null
            currentActionMode?.finish()
        }

        val barContainer = findViewById<View>(R.id.selection_bar) ?: 
                           findViewById<View>(R.id.selection_bar_container) ?: run {
                               android.util.Log.e("SelectionBar", "Bar container NOT FOUND")
                               return
                           }

        val newVisibility = if (show) View.VISIBLE else View.GONE
        if (barContainer.visibility != newVisibility) {
            barContainer.visibility = newVisibility
            if (show) {
                barContainer.alpha = 1f
            }
        }

        if (show) {
            val countText = findViewById<TextView>(R.id.selection_count)
            countText?.text = "$count selected"
            countText?.setTextColor(config.topBarTextColor)

            val senderInfoText = findViewById<TextView>(R.id.selection_sender_info)
            senderInfoText?.beVisibleIf(!senderInfo.isNullOrEmpty())
            senderInfoText?.text = senderInfo
            senderInfoText?.setTextColor(config.topBarTextColor)
            
            val tint = ColorStateList.valueOf(config.topBarTextColor)
            
            findViewById<ImageView>(R.id.selection_cancel)?.apply {
                imageTintList = tint
                setOnClickListener { onAction(R.id.selection_cancel) }
            }
            
            // Sync Bar Gradient
            val baseColor = if (config.dominantColor != 0) config.dominantColor else (if (config.topBarColor != 0) config.topBarColor else Color.BLACK)
            val lightened = adjustColor(baseColor, 1.2f)
            val darkened = adjustColor(baseColor, 0.8f)
            val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lightened, baseColor, darkened))
            gd.cornerRadius = 1000f // Pill shape
            findViewById<View>(R.id.selection_bar_pill)?.background = gd

            // Action Mapping
            val actionMap = mapOf(
                R.id.action_select_all to R.id.cab_select_all,
                R.id.action_delete to R.id.cab_delete,
                R.id.action_copy to listOf(R.id.cab_copy_to_clipboard, R.id.cab_copy_number),
                R.id.action_download to R.id.cab_save_as,
                R.id.action_archive to R.id.cab_archive,
                R.id.action_pin to R.id.cab_pin_conversation,
                R.id.action_unpin to R.id.cab_unpin_conversation
            )

            actionMap.forEach { (viewId, cabIds) ->
                findViewById<View>(viewId)?.apply {
                    val targetCabId = if (cabIds is List<*>) {
                        (cabIds as List<Int>).firstOrNull { actions.contains(it) } ?: -1
                    } else {
                        cabIds as Int
                    }
                    
                    if (targetCabId != -1 && actions.contains(targetCabId)) {
                        visibility = View.VISIBLE
                        if (this is ImageView) imageTintList = tint
                        setOnClickListener { onAction(targetCabId) }
                    } else {
                        visibility = View.GONE
                    }
                }
            }
        } else {
            barContainer.visibility = View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
    }

    private fun requestHighRefreshRate() {
        if (isRPlus()) {
            try {
                val display = display
                val modes = display?.supportedModes
                val maxRefreshRate = modes?.maxByOrNull { it.refreshRate }?.refreshRate ?: 0f
                if (maxRefreshRate > 60f) {
                    window.attributes.preferredRefreshRate = maxRefreshRate
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    override fun getRepositoryName() = "Messages"

    fun showModernMenu(anchor: View, items: List<Pair<Int, String>>, callback: (Int) -> Unit) {
        val popup = ListPopupWindow(this)
        
        val baseBarColor = if (config.topBarColor == 0) Color.BLACK else config.topBarColor
        val barColor = if (config.dominantColor != 0) config.dominantColor else baseBarColor
        val barTextColor = config.topBarTextColor
        
        // Safety: Ensure text is visible against the background
        val finalTextColor = if (barTextColor == barColor || barTextColor == Color.TRANSPARENT) {
            if (barColor == Color.WHITE) Color.BLACK else Color.WHITE
        } else {
            barTextColor
        }

        val adapter = object : ArrayAdapter<Pair<Int, String>>(this, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.text = items[position].second
                view.setTextColor(finalTextColor)
                view.setPadding(24.getScaledPx(), 10.getScaledPx(), 24.getScaledPx(), 10.getScaledPx())
                view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.85f))
                view.typeface = android.graphics.Typeface.create(getCustomTypeface(), android.graphics.Typeface.BOLD)
                return view
            }
        }

        popup.setAdapter(adapter)
        popup.anchorView = anchor
        popup.width = 240.getScaledPx()
        popup.isModal = true
        popup.setDropDownGravity(android.view.Gravity.END)
        popup.horizontalOffset = (-10).getScaledPx()
        popup.verticalOffset = 16.getScaledPx()
        
        val radius = 18f * resources.displayMetrics.density
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(barColor)
            // Add a subtle border for depth
            val strokeColor = if (barColor == Color.BLACK) Color.DKGRAY else barColor.withAlpha(0.8f)
            setStroke(1.getScaledPx(), strokeColor)
        }
        popup.setBackgroundDrawable(background)
        
        popup.setOnItemClickListener { _, _, position, _ ->
            callback(items[position].first)
            popup.dismiss()
        }
        
        popup.show()
    }

    private fun clearGlideTarget(view: View) {
        (view.tag as? CustomTarget<*>)?.let {
            Glide.with(this).clear(it)
            view.tag = null
        }
        Glide.with(this).clear(view)
    }

    class DirectCropTransformation(private val rect: RectF) : BitmapTransformation() {
        override fun transform(pool: com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
            val left = (rect.left * toTransform.width).toInt().coerceIn(0, toTransform.width - 1)
            val top = (rect.top * toTransform.height).toInt().coerceIn(0, toTransform.height - 1)
            val right = (rect.right * toTransform.width).toInt().coerceIn(left + 1, toTransform.width)
            val bottom = (rect.bottom * toTransform.height).toInt().coerceIn(top + 1, toTransform.height)
            
            val width = right - left
            val height = bottom - top
            return Bitmap.createBitmap(toTransform, left, top, width, height)
        }

        override fun updateDiskCacheKey(messageDigest: MessageDigest) {
            messageDigest.update("direct_crop_${rect.left}_${rect.top}_${rect.right}_${rect.bottom}".toByteArray())
        }
    }

    private fun loadBackgroundImage(source: String, targetView: View, cropRect: String = "", onLoaded: (() -> Unit)? = null) {
        if (source.isBlank()) return
        
        clearGlideTarget(targetView)
        
        val normalizedRect = try {
            if (cropRect.isNotEmpty()) {
                val parts = cropRect.split(",").map { it.toFloat() }
                RectF(parts[0], parts[1], parts[2], parts[3])
            } else null
        } catch (_: Exception) { null }

        val target = object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                targetView.background = resource
                
                // Extract dominant color if it's an AppBarLayout (Top Bar)
                if (targetView is AppBarLayout || targetView.id == R.id.settings_appbar || targetView.id == R.id.main_appbar || targetView.id == R.id.thread_appbar || targetView.id == R.id.new_conversation_appbar) {
                    if (resource is android.graphics.drawable.BitmapDrawable) {
                        Palette.from(resource.bitmap).generate { palette ->
                            val color = palette?.getDominantColor(config.topBarColor) ?: 0
                            if (color != 0) {
                                config.dominantColor = color
                            }
                        }
                    }
                }
                
                onLoaded?.invoke()
            }
            override fun onLoadFailed(errorDrawable: Drawable?) {}
            override fun onLoadCleared(placeholder: Drawable?) {}
        }
        
        targetView.tag = target

        var builder = Glide.with(this)
            .load(source)
            .diskCacheStrategy(DiskCacheStrategy.ALL)

        if (normalizedRect != null) {
            builder = builder.transform(DirectCropTransformation(normalizedRect))
        } else {
            builder = builder.centerCrop()
        }
        
        builder.into(target)
    }
}

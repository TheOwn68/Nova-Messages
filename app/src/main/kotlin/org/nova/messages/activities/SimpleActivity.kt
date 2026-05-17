package org.nova.messages.activities

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.toColorInt
import androidx.core.view.updateLayoutParams
import com.google.android.material.appbar.AppBarLayout
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.extensions.getTextSize
import org.nova.messages.R
import org.nova.messages.extensions.config
import kotlin.math.max

open class SimpleActivity : BaseSimpleActivity() {

    val uiScale get() = config.uiScale

    override fun getPackageName(): String {
        val stackTrace = Thread.currentThread().stackTrace
        for (element in stackTrace) {
            val className = element.className
            val methodName = element.methodName
            
            if (className.contains("org.fossify.commons")) {
                if ((methodName == "onCreate") || 
                    (methodName == "appLaunched") || 
                    methodName.contains("Warning") || 
                    methodName.contains("Sideload") ||
                    methodName.contains("Security")) {
                    return "org.fossify.messages"
                }
            }

            if (className.startsWith("android.app.") || 
                className.startsWith("androidx.") ||
                className.startsWith("android.content.pm.")) {
                break
            }
        }
        return super.getPackageName()
    }

    override fun getApplicationInfo(): ApplicationInfo {
        val info = super.getApplicationInfo()
        val stackTrace = Thread.currentThread().stackTrace
        for (element in stackTrace) {
            val className = element.className
            val methodName = element.methodName
            
            if (className.startsWith("android.app.") || 
                className.startsWith("androidx.") ||
                className.startsWith("android.content.pm.")) {
                break
            }

            if (className.contains("org.fossify.commons")) {
                if ((methodName == "onCreate") || 
                    (methodName == "appLaunched") || 
                    methodName.contains("Warning") || 
                    methodName.contains("Sideload") ||
                    methodName.contains("Security")) {
                    val spoofedInfo = ApplicationInfo(info)
                    spoofedInfo.packageName = "org.fossify.messages"
                    return spoofedInfo
                }
            }
        }
        return info
    }

    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        applyCustomColors()
        updateAppFonts(findViewById(android.R.id.content))
    }

    fun getScaledTextSize(multiplier: Float = 1.0f): Float {
        return getTextSize() * multiplier
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

    fun getCustomTypeface(): android.graphics.Typeface {
        return when (config.fontFamilyNova) {
            1 -> android.graphics.Typeface.SERIF
            2 -> android.graphics.Typeface.MONOSPACE
            3 -> android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL)
            4 -> android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL)
            5 -> android.graphics.Typeface.SANS_SERIF
            6 -> android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            7 -> android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
            else -> android.graphics.Typeface.DEFAULT
        }
    }

    fun updateAppFonts(view: View?) {
        if (view == null) return
        val customTypeface = getCustomTypeface()
        if (view is TextView) {
            val style = view.typeface?.style ?: android.graphics.Typeface.NORMAL
            view.typeface = android.graphics.Typeface.create(customTypeface, style)
            
            // Apply custom text color if not in toolbar AND not a message bubble
            val id = view.id
            val isExcluded = id == R.id.thread_toolbar_title || 
                             id == R.id.nova_title || 
                             id == R.id.settings_toolbar_title ||
                             id == R.id.thread_message_body ||
                             id == R.id.nova_search_input ||
                             id == R.id.thread_type_message
                             
            if (!isExcluded) {
                view.setTextColor(config.mainTextColor)
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
        window.decorView.setBackgroundColor(config.mainBackgroundColor)
        
        // Ensure all possible coordinators are transparent
        val rootView = findViewById<View>(android.R.id.content)
        if (rootView is ViewGroup) {
            forceTransparentContainers(rootView)
        }
        
        // 2. Apply top bar color (HARD RECURSIVE SHAPE GUARD)
        val appBar = findViewById<AppBarLayout>(R.id.settings_appbar) ?: 
                     findViewById<AppBarLayout>(R.id.thread_appbar) ?: 
                     findViewById<AppBarLayout>(R.id.main_appbar)
                     
        val toolbar = findViewById<Toolbar>(R.id.settings_toolbar) ?: 
                      findViewById<Toolbar>(R.id.thread_toolbar) ?: 
                      findViewById<Toolbar>(R.id.main_toolbar)
        
        if (appBar != null) {
            val barColor = if (config.topBarColor != -1) config.topBarColor else Color.BLACK
            val barRadius = 26 * density
            
            val barShape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, barRadius, barRadius, barRadius, barRadius)
                setColor(barColor)
            }
            
            appBar.isLiftOnScroll = false
            appBar.elevation = 0f
            appBar.background = barShape
            
            // Attach a scroll listener that FORCES the shape at every pixel of scroll
            if (appBar.tag != "shape_guard_attached") {
                appBar.addOnOffsetChangedListener { _, _ ->
                    appBar.background = barShape
                    appBar.elevation = 0f
                    appBar.stateListAnimator = null // Kill the "lift" animator completely
                }
                appBar.tag = "shape_guard_attached"
            }
        }
        
        toolbar?.setBackgroundColor(Color.TRANSPARENT)
        
        // 3. Apply top bar text color
        if (toolbar != null) {
            toolbar.setTitleTextColor(config.topBarTextColor)
            toolbar.navigationIcon?.setTint(config.topBarTextColor)
            toolbar.overflowIcon?.setTint(config.topBarTextColor)
        }
        
        val titleText = findViewById<TextView>(R.id.thread_toolbar_title) ?: 
                         findViewById<TextView>(R.id.nova_title) ?: 
                         findViewById<TextView>(R.id.settings_toolbar_title)
        titleText?.setTextColor(config.topBarTextColor)
        
        // 4. Apply input bar colors (Maintaining Rounded Shape)
        val inputBar = findViewById<View>(R.id.nova_search_bar) ?: findViewById<View>(R.id.nova_message_input_bar)
        if (inputBar != null) {
            val inputBgColor = config.inputBarBackgroundColor
            val inputRadius = 28 * density 
            
            val inputShape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = inputRadius
                setColor(inputBgColor)
            }
            inputBar.background = inputShape
        }
        
        // 5. Targeted EditText coloring for live typed text
        val inputEditTexts = listOfNotNull(
            findViewById<EditText>(R.id.nova_search_input),
            findViewById<EditText>(R.id.thread_type_message),
        )
        
        val textColorCSL = ColorStateList.valueOf(config.inputBarTextColor)
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
        
        // Apply gear icon color
        findViewById<ImageView>(R.id.settings_gear)?.let {
            it.imageTintList = ColorStateList.valueOf(config.topBarTextColor)
            it.alpha = 1.0f
        }
    }

    private fun forceTransparentContainers(view: View) {
        val id = view.id
        if (id == R.id.main_coordinator || 
            id == R.id.settings_coordinator || 
            id == R.id.thread_coordinator ||
            id == R.id.main_nested_scrollview || 
            id == R.id.main_coordinator_wrapper ||
            id == R.id.main_holder ||
            id == R.id.thread_holder ||
            id == R.id.message_holder) {
            view.setBackgroundColor(Color.TRANSPARENT)
        }
        
        (view as? ViewGroup)?.let {
            for (i in 0 until it.childCount) {
                forceTransparentContainers(it.getChildAt(i))
            }
        }
    }

    private fun Int.withAlpha(alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (this and 0x00FFFFFF) or (a shl 24)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("FATAL_CRASH", "CRASH IN THREAD: ${thread.name}", throwable)
            oldHandler?.uncaughtException(thread, throwable)
        }

        super.onCreate(savedInstanceState)
        requestHighRefreshRate()
    }

    override fun onResume() {
        super.onResume()
        findViewById<View>(android.R.id.content)?.viewTreeObserver?.addOnGlobalLayoutListener(globalLayoutListener)
        applyCustomColors()
        updateAppFonts(findViewById(android.R.id.content))
    }

    override fun onPause() {
        super.onPause()
        findViewById<View>(android.R.id.content)?.viewTreeObserver?.removeOnGlobalLayoutListener(globalLayoutListener)
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
        R.mipmap.ic_launcher_red,
        R.mipmap.ic_launcher_pink,
        R.mipmap.ic_launcher_purple,
        R.mipmap.ic_launcher_deep_purple,
        R.mipmap.ic_launcher_indigo,
        R.mipmap.ic_launcher_blue,
        R.mipmap.ic_launcher_light_blue,
        R.mipmap.ic_launcher_cyan,
        R.mipmap.ic_launcher_teal,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher_light_green,
        R.mipmap.ic_launcher_lime,
        R.mipmap.ic_launcher_yellow,
        R.mipmap.ic_launcher_amber,
        R.mipmap.ic_launcher_orange,
        R.mipmap.ic_launcher_deep_orange,
        R.mipmap.ic_launcher_brown,
        R.mipmap.ic_launcher_blue_grey,
        R.mipmap.ic_launcher_grey_black
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    override fun getRepositoryName() = "Messages"
}

package org.nova.messages.activities

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.updateLayoutParams
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.helpers.FONT_SIZE_EXTRA_LARGE
import org.fossify.commons.helpers.FONT_SIZE_LARGE
import org.fossify.commons.helpers.FONT_SIZE_MEDIUM
import org.fossify.commons.helpers.FONT_SIZE_SMALL
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.extensions.getTextSize
import org.nova.messages.R
import org.nova.messages.extensions.config
import org.nova.messages.helpers.Config

open class SimpleActivity : BaseSimpleActivity() {

    val uiScale get() = config.uiScale

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
        val finalHeight = Math.max(baseHeight, minHeightRequired)
        
        toolbar.updateLayoutParams {
            height = finalHeight
        }
    }

    fun getCustomTypeface(): android.graphics.Typeface {
        val fontFamily = config.fontFamilyNova
        return when (fontFamily) {
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

    fun updateAppFonts(view: View) {
        val customTypeface = getCustomTypeface()
        if (view is TextView) {
            val style = view.typeface?.style ?: android.graphics.Typeface.NORMAL
            view.typeface = android.graphics.Typeface.create(customTypeface, style)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                updateAppFonts(view.getChildAt(i))
            }
        }
    }

    override fun getPackageName(): String {
        val stackTrace = Thread.currentThread().stackTrace
        for (element in stackTrace) {
            val className = element.className
            val methodName = element.methodName
            if (className.contains("org.fossify.commons") && 
                (methodName == "showModdedAppWarning" || 
                 methodName == "checkAppSideloading" || 
                 methodName == "isAppSideloaded" ||
                 methodName == "appLaunched")) {
                return "org.fossify.messages"
            }
        }
        return super.getPackageName()
    }

    override fun getApplicationInfo(): android.content.pm.ApplicationInfo {
        val info = super.getApplicationInfo()
        val stackTrace = Thread.currentThread().stackTrace
        for (element in stackTrace) {
            val className = element.className
            val methodName = element.methodName
            if (className.contains("org.fossify.commons") && 
                (methodName == "showModdedAppWarning" || 
                 methodName == "checkAppSideloading" || 
                 methodName == "isAppSideloaded" ||
                 methodName == "appLaunched")) {
                val spoofedInfo = android.content.pm.ApplicationInfo(info)
                spoofedInfo.packageName = "org.fossify.messages"
                return spoofedInfo
            }
        }
        return info
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
        updateAppFonts(findViewById(android.R.id.content))
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
            } catch (ignored: Exception) {
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

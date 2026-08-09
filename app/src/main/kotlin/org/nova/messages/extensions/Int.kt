package org.nova.messages.extensions

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

fun Int.withAlpha(alpha: Float): Int {
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    return (this and 0x00FFFFFF) or (a shl 24)
}

fun Int.adjustColor(factor: Float): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(this, hsv)
    hsv[2] *= factor
    return Color.HSVToColor(hsv)
}

fun Int.getScaledPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}

fun Int.getScaledPx(): Int {
    return (this * 2.5).toInt() 
}

fun Float.getScaledTextSize(context: Context): Float {
    return this * context.config.uiScale
}

fun Context.getScaledTextSize(size: Float = 16f): Float {
    return size * config.uiScale
}

fun Context.getScaledPx(px: Int): Int {
    return (px * resources.displayMetrics.density).toInt()
}

fun Context.getScaledDimen(resId: Int): Int {
    return resources.getDimensionPixelSize(resId)
}

fun Int.getScaledDimen(context: Context): Int {
    return context.resources.getDimensionPixelSize(this)
}

fun Context.getCustomTypeface(): Typeface? = null

fun View.updateAppFonts() {
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).updateAppFonts()
        }
    } else if (this is TextView) {
        val typeface = context.getCustomTypeface()
        if (typeface != null) {
            this.typeface = typeface
        }
    }
}

fun Context.updateAppFonts(view: View) {
    view.updateAppFonts()
}

fun Context.setupScaledToolbar(toolbar: View) {}

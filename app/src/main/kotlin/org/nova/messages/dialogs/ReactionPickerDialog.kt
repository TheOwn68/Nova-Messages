package org.nova.messages.dialogs

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import org.fossify.commons.extensions.applyColorFilter
import org.nova.messages.extensions.config

class ReactionPickerDialog(
    private val context: Context,
    private val anchor: View,
    private val onReactionSelected: (String) -> Unit
) {
    private val popupWindow: PopupWindow
    private val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "😡", "👎", "⁉️", "❓", "🔥", "💯", "👏", "✅", "🎉")

    init {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val padding = (8 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val scrollView = HorizontalScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                (280 * context.resources.displayMetrics.density).toInt(), // Max width to ensure it doesn't go off screen
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            
            background = context.getDrawable(org.fossify.commons.R.drawable.pill_background).apply {
                // Background color same as top bar
                val color = if (context.config.topBarColor != 0) context.config.topBarColor else context.config.primaryColor
                this?.applyColorFilter(color)
            }
            elevation = 10 * context.resources.displayMetrics.density
            addView(container)
        }

        emojis.forEach { emoji ->
            val textView = TextView(context).apply {
                text = emoji
                textSize = 24f
                val paddingEmoji = (12 * context.resources.displayMetrics.density).toInt()
                setPadding(paddingEmoji, paddingEmoji, paddingEmoji, paddingEmoji)
                isClickable = true
                focusable = View.FOCUSABLE
                setTextColor(Color.WHITE)
                
                setOnClickListener {
                    onReactionSelected(emoji)
                    popupWindow.dismiss()
                }
            }
            container.addView(textView)
        }

        popupWindow = PopupWindow(
            scrollView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 15f
            isOutsideTouchable = true
        }
    }

    fun show() {
        if (anchor.windowToken == null) return
        
        anchor.post {
            try {
                // Show right under the anchor, centered horizontally
                popupWindow.showAsDropDown(anchor, 0, (4 * context.resources.displayMetrics.density).toInt(), Gravity.CENTER_HORIZONTAL)
                android.util.Log.d("ReactionPicker", "PopupWindow shown with topBarColor: ${context.config.topBarColor}")
            } catch (e: Exception) {
                android.util.Log.e("ReactionPicker", "Error showing popup", e)
            }
        }
    }
}

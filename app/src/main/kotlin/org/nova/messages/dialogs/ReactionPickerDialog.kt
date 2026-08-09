package org.nova.messages.dialogs

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.nova.messages.R
import org.nova.messages.extensions.config

class ReactionPickerDialog(
    private val context: Context,
    private val anchor: View,
    private val onReactionSelected: (String) -> Unit
) {
    private val dialog: BottomSheetDialog
    private val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "😡", "👎", "⁉️", "❓", "🔥", "💯", "👏", "✅", "🎉")

    init {
        val density = context.resources.displayMetrics.density
        val config = context.config
        val isNewUi = config.useNewUi
        
        val barColor = if (config.topBarColor != 0) config.topBarColor else Color.BLACK
        val barRadius = 26 * density
        // Use Top Bar outline thickness for the picker as well
        val thickStroke = (config.topBarOutlineThickness * density).toInt()
        
        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, (24 * density).toInt())
        }

        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            
            val background = if (isNewUi) {
                val lightened = adjustColor(barColor, 1.2f)
                val darkened = adjustColor(barColor, 0.8f)
                GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lightened, barColor, darkened)).apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = barRadius
                    
                    if (config.topBarOutline) {
                        setStroke(thickStroke, config.topBarOutlineColor)
                    }
                }
            } else {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = barRadius
                    setColor(barColor)
                }
            }
            
            if (isNewUi && config.topBarOutline) {
                val layerDrawable = LayerDrawable(arrayOf(background))
                layerDrawable.setLayerInset(0, 0, 0, 0, 0)
                this.background = layerDrawable
            } else {
                this.background = background
            }
            
            elevation = 14 * density
            
            val pillWidth = (440 * density * config.uiScale).toInt()
            layoutParams = FrameLayout.LayoutParams(pillWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        root.addView(pill)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val px = (12 * density).toInt()
            setPadding(px, 0, px, 0)
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (60 * density * config.uiScale).toInt())
        }

        val scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(container)
        }
        pill.addView(scrollView)

        emojis.forEach { emoji ->
            val textView = TextView(context).apply {
                text = emoji
                textSize = 32f * config.uiScale
                val p = (8 * density).toInt()
                setPadding(p, 0, p, 0)
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                
                setOnClickListener {
                    onReactionSelected(emoji)
                    dialog.dismiss()
                }
            }
            container.addView(textView)
        }

        dialog = BottomSheetDialog(context, R.style.TransparentBottomSheetDialogTheme)
        dialog.setContentView(root)
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                window.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
                    background = null
                    setBackgroundColor(Color.TRANSPARENT)
                }
            }
        }
    }

    private fun adjustColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = Math.round(Color.red(color) * factor).coerceIn(0, 255)
        val g = Math.round(Color.green(color) * factor).coerceIn(0, 255)
        val b = Math.round(Color.blue(color) * factor).coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    fun show() {
        try {
            dialog.show()
        } catch (e: Exception) {
            android.util.Log.e("ReactionInteraction", "Failed to show BottomSheetDialog", e)
        }
    }
}

package org.nova.messages.helpers

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import org.nova.messages.extensions.config

object ReactionStyleUtils {
    fun setupReactionBubble(view: TextView, context: Context) {
        val config = context.config
        val padding = (4 * context.resources.displayMetrics.density).toInt()
        val cornerRadius = (12 * context.resources.displayMetrics.density)
        
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(config.primaryColor)
            setStroke((1 * context.resources.displayMetrics.density).toInt(), Color.WHITE)
            setCornerRadius(cornerRadius)
        }
        
        view.background = background
        view.setPadding(padding * 2, padding, padding * 2, padding)
        view.setTextColor(config.textColor)
        view.textSize = 12f
    }
}

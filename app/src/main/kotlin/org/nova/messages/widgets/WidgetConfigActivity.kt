package org.nova.messages.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.nova.messages.R

class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var bgColor = Color.parseColor("#CC1A1A1A")
    private var textColor = Color.WHITE
    private var bgImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val prefs = getSharedPreferences("widget_prefs_$appWidgetId", Context.MODE_PRIVATE)
        bgColor = prefs.getInt("bg_color", Color.parseColor("#CC1A1A1A"))
        textColor = prefs.getInt("text_color", Color.WHITE)
        bgImageUri = prefs.getString("bg_image", null)?.let { Uri.parse(it) }

        // Preview
        val preview = findViewById<TextView>(R.id.config_preview)
        preview.setBackgroundColor(bgColor)
        preview.setTextColor(textColor)

        // Background color buttons
        findViewById<Button>(R.id.btn_bg_dark).setOnClickListener {
            bgColor = Color.parseColor("#CC1A1A1A")
            bgImageUri = null
            preview.setBackgroundColor(bgColor)
        }
        findViewById<Button>(R.id.btn_bg_light).setOnClickListener {
            bgColor = Color.parseColor("#CCFFFFFF")
            bgImageUri = null
            preview.setBackgroundColor(bgColor)
            preview.setTextColor(if (textColor == Color.WHITE) Color.BLACK else textColor)
        }
        findViewById<Button>(R.id.btn_bg_transparent).setOnClickListener {
            bgColor = Color.TRANSPARENT
            bgImageUri = null
            preview.setBackgroundColor(bgColor)
        }

        // Text color buttons
        findViewById<Button>(R.id.btn_text_white).setOnClickListener {
            textColor = Color.WHITE
            preview.setTextColor(textColor)
        }
        findViewById<Button>(R.id.btn_text_black).setOnClickListener {
            textColor = Color.BLACK
            preview.setTextColor(textColor)
        }
        findViewById<Button>(R.id.btn_text_accent).setOnClickListener {
            textColor = Color.parseColor("#FF4081")
            preview.setTextColor(textColor)
        }

        // Save
        findViewById<Button>(R.id.btn_save).setOnClickListener {
            prefs.edit()
                .putInt("bg_color", bgColor)
                .putInt("text_color", textColor)
                .putString("bg_image", bgImageUri?.toString())
                .apply()

            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(Activity.RESULT_OK, resultValue)

            // Trigger widget update
            ConversationsListWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), appWidgetId)
            finish()
        }
    }
}
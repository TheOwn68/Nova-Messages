package org.nova.messages.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.SimpleContactsHelper
import org.nova.messages.activities.SimpleActivity
import org.nova.messages.databinding.ActivityWidgetConfigBinding
import org.nova.messages.extensions.config

class WidgetConfigActivity : SimpleActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var bgColor = Color.parseColor("#CC1A1A1A")
    private var textColor = Color.WHITE

    private val binding by viewBinding(ActivityWidgetConfigBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.widgetConfigRoot))
        setupTopAppBar(binding.widgetConfigAppbar, NavigationIcon.Arrow, Color.TRANSPARENT)
        binding.widgetConfigToolbar.setNavigationOnClickListener { finish() }

        val prefs = getSharedPreferences("widget_prefs_$appWidgetId", Context.MODE_PRIVATE)
        bgColor = prefs.getInt("bg_color", Color.parseColor("#CC1A1A1A"))
        textColor = prefs.getInt("text_color", config.mainTextColor)

        updatePreviewUI()

        // Background choices
        binding.btnBgDark.setOnClickListener {
            bgColor = Color.parseColor("#CC1A1A1A")
            updatePreviewUI()
        }
        binding.btnBgTranslucent.setOnClickListener {
            bgColor = Color.parseColor("#22FFFFFF")
            updatePreviewUI()
        }
        binding.btnBgLight.setOnClickListener {
            bgColor = Color.parseColor("#EEEEEE")
            updatePreviewUI()
        }
        binding.btnBgTransparent.setOnClickListener {
            bgColor = Color.TRANSPARENT
            updatePreviewUI()
        }

        // Text choices
        binding.btnTextWhite.setOnClickListener {
            textColor = Color.WHITE
            updatePreviewUI()
        }
        binding.btnTextBlack.setOnClickListener {
            textColor = Color.BLACK
            updatePreviewUI()
        }
        binding.btnTextAccent.setOnClickListener {
            textColor = getProperPrimaryColor()
            updatePreviewUI()
        }

        // Save
        binding.btnSave.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 1000f
                setColor(getProperPrimaryColor())
            }
            setOnClickListener {
                prefs.edit()
                    .putInt("bg_color", bgColor)
                    .putInt("text_color", textColor)
                    .apply()

                val resultValue = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                setResult(Activity.RESULT_OK, resultValue)

                ConversationsListWidgetProvider.updateWidget(this@WidgetConfigActivity, AppWidgetManager.getInstance(this@WidgetConfigActivity), appWidgetId)
                finish()
            }
        }
    }

    private fun updatePreviewUI() = binding.apply {
        val density = resources.displayMetrics.density
        configPreviewFrame.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f * density
            setColor(bgColor)
        }

        val previewItem = configPreviewItem
        previewItem.widgetItemTitle.apply {
            text = "Jane Doe"
            setTextColor(textColor)
        }
        previewItem.widgetItemSnippet.apply {
            text = "Hey! See you tonight at 8?"
            setTextColor(textColor)
        }

        val avatarBitmap = SimpleContactsHelper(this@WidgetConfigActivity).getContactLetterIcon("Jane Doe")
        if (avatarBitmap != null) {
            previewItem.widgetItemAvatar.setImageBitmap(avatarBitmap)
        }

        // Set buttons styling
        val mainTextColor = config.mainTextColor
        val buttonBg = { isSelected: Boolean ->
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 1000f
                if (isSelected) {
                    setColor(getProperPrimaryColor())
                } else {
                    setColor(Color.parseColor("#16FFFFFF"))
                }
            }
        }

        btnBgDark.background = buttonBg(bgColor == Color.parseColor("#CC1A1A1A"))
        btnBgTranslucent.background = buttonBg(bgColor == Color.parseColor("#22FFFFFF"))
        btnBgLight.background = buttonBg(bgColor == Color.parseColor("#EEEEEE"))
        btnBgTransparent.background = buttonBg(bgColor == Color.TRANSPARENT)

        btnTextWhite.background = buttonBg(textColor == Color.WHITE)
        btnTextBlack.background = buttonBg(textColor == Color.BLACK)
        btnTextAccent.background = buttonBg(textColor == getProperPrimaryColor())

        configPreviewLabel.setTextColor(mainTextColor)
        configBgLabel.setTextColor(mainTextColor)
        configTextLabel.setTextColor(mainTextColor)
        widgetConfigToolbarTitle.setTextColor(mainTextColor)
    }
}

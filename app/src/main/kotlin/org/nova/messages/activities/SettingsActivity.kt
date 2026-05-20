package org.nova.messages.activities

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import com.google.android.material.slider.Slider
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getFontSizeText
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.FONT_SIZE_EXTRA_LARGE
import org.fossify.commons.helpers.FONT_SIZE_LARGE
import org.fossify.commons.helpers.FONT_SIZE_MEDIUM
import org.fossify.commons.helpers.FONT_SIZE_SMALL
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.RadioItem
import org.fossify.commons.views.MyAppBarLayout
import org.nova.messages.R
import org.nova.messages.databinding.ActivitySettingsBinding
import org.nova.messages.databinding.DialogColorPickerBinding
import org.nova.messages.extensions.config
import org.nova.messages.helpers.*

class SettingsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.settingsNestedScrollview))
    }

    override fun onResume() {
        super.onResume()
        // Use a safe cast to avoid ClassCastException
        (binding.settingsAppbar as? MyAppBarLayout)?.let { appBar ->
            setupTopAppBar(appBar, NavigationIcon.Arrow, Color.TRANSPARENT)
        }
        binding.settingsToolbar.navigationIcon?.setTint(config.topBarTextColor)
        binding.settingsToolbar.setTitleTextColor(config.topBarTextColor)
        setupScaledToolbar(binding.settingsToolbar)

        setupUIScale()
        setupFontSize()
        setupFont()
        setupCustomization()
        updateAppFonts(binding.root)

        val mainTextColor = config.mainTextColor
        binding.settingsCustomizationLabel.setTextColor(mainTextColor)
        binding.settingsBubbleCustomizationLabel.setTextColor(mainTextColor)
        binding.settingsGeneralLabel.setTextColor(mainTextColor)
        binding.settingsResetDefaults.setTextColor(mainTextColor)

        applyCustomColors()
    }

    private fun setupCustomization() = binding.apply {
        fun updatePreview(view: android.view.View, color: Int) {
            val drawable = view.background as GradientDrawable
            drawable.setColor(color)
        }

        updatePreview(settingsTopBarColorPreview, if (config.topBarColor == -1) Color.BLACK else config.topBarColor)
        updatePreview(settingsTopBarTextColorPreview, config.topBarTextColor)
        updatePreview(settingsMainBackgroundColorPreview, config.mainBackgroundColor)
        updatePreview(settingsMainTextColorPreview, config.mainTextColor)
        updatePreview(settingsInputBarBackgroundColorPreview, config.inputBarBackgroundColor)
        updatePreview(settingsInputBarTextColorPreview, config.inputBarTextColor)
        
        updatePreview(settingsSentBubbleColorPreview, config.sentBubbleColor)
        updatePreview(settingsSentBubbleTextColorPreview, config.sentBubbleTextColor)
        updatePreview(settingsReceivedBubbleColorPreview, config.receivedBubbleColor)
        updatePreview(settingsReceivedBubbleTextColorPreview, config.receivedBubbleTextColor)

        settingsTopBarColorHolder.setOnClickListener {
            showColorWheel(if (config.topBarColor == -1) Color.BLACK else config.topBarColor) { config.topBarColor = it; applyCustomColors(); recreate() }
        }

        settingsTopBarTextColorHolder.setOnClickListener {
            showColorWheel(config.topBarTextColor) { config.topBarTextColor = it; applyCustomColors(); recreate() }
        }

        settingsMainBackgroundColorHolder.setOnClickListener {
            showColorWheel(config.mainBackgroundColor) { config.mainBackgroundColor = it; applyCustomColors(); recreate() }
        }

        settingsMainTextColorHolder.setOnClickListener {
            showColorWheel(config.mainTextColor) { config.mainTextColor = it; applyCustomColors(); recreate() }
        }

        settingsInputBarBackgroundColorHolder.setOnClickListener {
            showColorWheel(config.inputBarBackgroundColor) { config.inputBarBackgroundColor = it; applyCustomColors(); recreate() }
        }

        settingsInputBarTextColorHolder.setOnClickListener {
            showColorWheel(config.inputBarTextColor) { config.inputBarTextColor = it; applyCustomColors(); recreate() }
        }

        settingsSentBubbleColorHolder.setOnClickListener {
            showColorWheel(config.sentBubbleColor) { config.sentBubbleColor = it; recreate() }
        }

        settingsSentBubbleTextColorHolder.setOnClickListener {
            showColorWheel(config.sentBubbleTextColor) { config.sentBubbleTextColor = it; recreate() }
        }

        settingsReceivedBubbleColorHolder.setOnClickListener {
            showColorWheel(config.receivedBubbleColor) { config.receivedBubbleColor = it; recreate() }
        }

        settingsReceivedBubbleTextColorHolder.setOnClickListener {
            showColorWheel(config.receivedBubbleTextColor) { config.receivedBubbleTextColor = it; recreate() }
        }

        settingsResetDefaults.setOnClickListener {
            config.topBarColor = -1
            config.topBarTextColor = Color.WHITE
            config.mainBackgroundColor = Color.WHITE
            config.mainTextColor = Color.BLACK
            config.inputBarBackgroundColor = Config.DEFAULT_DARK_GREY
            config.inputBarTextColor = Color.WHITE
            config.sentBubbleColor = Config.DEFAULT_SENT_GREY
            config.receivedBubbleColor = Config.DEFAULT_LIGHT_GREY
            config.sentBubbleTextColor = Color.BLACK
            config.receivedBubbleTextColor = Color.BLACK
            applyCustomColors()
            recreate()
        }
    }

    private fun showColorWheel(initialColor: Int, callback: (Int) -> Unit) {
        val pickerBinding = DialogColorPickerBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(pickerBinding.root)
            .create()

        var selectedColor = initialColor
        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)

        fun updateColor() {
            selectedColor = Color.HSVToColor(hsv)
            val drawable = pickerBinding.colorPickerPreview.background as GradientDrawable
            drawable.setColor(selectedColor)
        }

        pickerBinding.colorPickerHue.progress = hsv[0].toInt()
        pickerBinding.colorPickerSaturation.progress = (hsv[1] * 100).toInt()
        pickerBinding.colorPickerValue.progress = (hsv[2] * 100).toInt()
        
        val drawable = pickerBinding.colorPickerPreview.background as GradientDrawable
        drawable.setColor(initialColor)

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    when (seekBar?.id) {
                        R.id.color_picker_hue -> hsv[0] = progress.toFloat()
                        R.id.color_picker_saturation -> hsv[1] = progress.toFloat() / 100f
                        R.id.color_picker_value -> hsv[2] = progress.toFloat() / 100f
                    }
                    updateColor()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        pickerBinding.colorPickerHue.setOnSeekBarChangeListener(listener)
        pickerBinding.colorPickerSaturation.setOnSeekBarChangeListener(listener)
        pickerBinding.colorPickerValue.setOnSeekBarChangeListener(listener)

        pickerBinding.colorPickerCancel.setOnClickListener { dialog.dismiss() }
        pickerBinding.colorPickerOk.setOnClickListener {
            callback(selectedColor)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupUIScale() = binding.apply {
        settingsUiScaleSlider.value = config.uiScale
        settingsUiScaleSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                config.uiScale = value
            }
        }
        
        settingsUiScaleSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                recreate()
            }
        })
    }

    private fun setupFontSize() = binding.apply {
        settingsFontSize.text = getFontSizeText()
        settingsFontSizeHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(org.fossify.commons.R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(org.fossify.commons.R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(org.fossify.commons.R.string.large)),
                RadioItem(
                    FONT_SIZE_EXTRA_LARGE,
                    getString(org.fossify.commons.R.string.extra_large)
                )
            )

            RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                settingsFontSize.text = getFontSizeText()
                recreate()
            }
        }
    }

    private fun setupFont() = binding.apply {
        settingsFont.text = getFontText()
        settingsFontHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(0, "System"),
                RadioItem(1, "Serif"),
                RadioItem(2, "Monospace"),
                RadioItem(3, "Cursive"),
                RadioItem(4, "Casual"),
                RadioItem(5, "Sans-serif Light"),
                RadioItem(6, "Sans-serif Condensed")
            )

            RadioGroupDialog(this@SettingsActivity, items, config.fontFamilyNova) {
                config.fontFamilyNova = it as Int
                settingsFont.text = getFontText()
                org.fossify.commons.helpers.FontHelper.clearCache()
                recreate()
            }
        }
    }

    private fun getFontText(): String {
        return when (config.fontFamilyNova) {
            1 -> "Serif"
            2 -> "Monospace"
            3 -> "Cursive"
            4 -> "Casual"
            5 -> "Sans-serif Light"
            6 -> "Sans-serif Condensed"
            else -> "System"
        }
    }
}

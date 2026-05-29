package org.nova.messages.activities

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.PERMISSION_WRITE_STORAGE
import org.fossify.commons.views.MyAppBarLayout
import org.nova.messages.R
import org.nova.messages.databinding.ActivitySettingsBinding
import org.nova.messages.extensions.config

class SettingsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.settingsNestedScrollview))
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow, Color.TRANSPARENT)

        (binding.settingsAppbar as? MyAppBarLayout)?.let { appBar ->
            appBar.setBackgroundColor(Color.TRANSPARENT)
            binding.settingsToolbar.navigationIcon?.setTint(config.topBarTextColor)
            binding.settingsToolbar.setNavigationOnClickListener { finish() }
        }

        setupCustomization()
        setupUIScale()
        setupNewUi()
        setupFontSize()
        setupFontFamily()
        updateAppFonts(binding.root)
    }

    private fun setupCustomization() = binding.apply {
        val mainTextColor = config.mainTextColor
        settingsCustomizationLabel.setTextColor(mainTextColor)
        settingsBubbleCustomizationLabel.setTextColor(mainTextColor)
        settingsGeneralLabel.setTextColor(mainTextColor)
        settingsNewUiColorsLabelHeader.setTextColor(mainTextColor)
        settingsResetDefaults.setTextColor(mainTextColor)

        val updatePreview = { view: View, color: Int ->
            view.background?.applyColorFilter(color)
        }

        updatePreview(settingsTopBarColorPreview, if (config.topBarColor == 0) Color.BLACK else config.topBarColor)
        updatePreview(settingsTopBarTextColorPreview, config.topBarTextColor)
        updatePreview(settingsMainBackgroundColorPreview, config.mainBackgroundColor)
        updatePreview(settingsMainTextColorPreview, config.mainTextColor)
        updatePreview(settingsInputBarBackgroundColorPreview, config.inputBarBackgroundColor)
        updatePreview(settingsInputBarTextColorPreview, config.inputBarTextColor)

        updatePreview(settingsSentBubbleColorPreview, config.sentBubbleColor)
        updatePreview(settingsSentBubbleTextColorPreview, config.sentBubbleTextColor)
        updatePreview(settingsReceivedBubbleColorPreview, config.receivedBubbleColor)
        updatePreview(settingsReceivedBubbleTextColorPreview, config.receivedBubbleTextColor)

        updatePreview(settingsColorRecentPreview, config.recentColor)
        updatePreview(settingsColorRow1Preview, config.row1Color)
        updatePreview(settingsColorRow2Preview, config.row2Color)
        updatePreview(settingsColorRow3Preview, config.row3Color)

        settingsTopBarColorHolder.setOnClickListener {
            val color = if (config.topBarColor == 0) Color.BLACK else config.topBarColor
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, color) { wasPositive, color ->
                if (wasPositive) {
                    config.topBarColor = if (color == Color.BLACK) 0 else color
                    updatePreview(settingsTopBarColorPreview, color)
                    applyCustomColors()
                }
            }
        }

        settingsTopBarTextColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.topBarTextColor) { wasPositive, color ->
                if (wasPositive) {
                    config.topBarTextColor = color
                    updatePreview(settingsTopBarTextColorPreview, color)
                    applyCustomColors()
                }
            }
        }

        settingsMainBackgroundColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.mainBackgroundColor) { wasPositive, color ->
                if (wasPositive) {
                    config.mainBackgroundColor = color
                    updatePreview(settingsMainBackgroundColorPreview, color)
                    applyCustomColors()
                }
            }
        }

        settingsMainTextColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.mainTextColor) { wasPositive, color ->
                if (wasPositive) {
                    config.mainTextColor = color
                    updatePreview(settingsMainTextColorPreview, color)
                    applyCustomColors()
                    updateAppFonts(binding.root)
                }
            }
        }

        settingsInputBarBackgroundColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.inputBarBackgroundColor) { wasPositive, color ->
                if (wasPositive) {
                    config.inputBarBackgroundColor = color
                    updatePreview(settingsInputBarBackgroundColorPreview, color)
                    applyCustomColors()
                }
            }
        }

        settingsInputBarTextColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.inputBarTextColor) { wasPositive, color ->
                if (wasPositive) {
                    config.inputBarTextColor = color
                    updatePreview(settingsInputBarTextColorPreview, color)
                    applyCustomColors()
                }
            }
        }

        settingsSentBubbleColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.sentBubbleColor) { wasPositive, color ->
                if (wasPositive) {
                    config.sentBubbleColor = color
                    updatePreview(settingsSentBubbleColorPreview, color)
                }
            }
        }

        settingsSentBubbleTextColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.sentBubbleTextColor) { wasPositive, color ->
                if (wasPositive) {
                    config.sentBubbleTextColor = color
                    updatePreview(settingsSentBubbleTextColorPreview, color)
                }
            }
        }

        settingsReceivedBubbleColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.receivedBubbleColor) { wasPositive, color ->
                if (wasPositive) {
                    config.receivedBubbleColor = color
                    updatePreview(settingsReceivedBubbleColorPreview, color)
                }
            }
        }

        settingsReceivedBubbleTextColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.receivedBubbleTextColor) { wasPositive, color ->
                if (wasPositive) {
                    config.receivedBubbleTextColor = color
                    updatePreview(settingsReceivedBubbleTextColorPreview, color)
                }
            }
        }

        settingsColorRecentHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.recentColor) { wasPositive, color ->
                if (wasPositive) {
                    config.recentColor = color
                    updatePreview(settingsColorRecentPreview, color)
                }
            }
        }

        settingsColorRow1Holder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.row1Color) { wasPositive, color ->
                if (wasPositive) {
                    config.row1Color = color
                    updatePreview(settingsColorRow1Preview, color)
                }
            }
        }

        settingsColorRow2Holder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.row2Color) { wasPositive, color ->
                if (wasPositive) {
                    config.row2Color = color
                    updatePreview(settingsColorRow2Preview, color)
                }
            }
        }

        settingsColorRow3Holder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.row3Color) { wasPositive, color ->
                if (wasPositive) {
                    config.row3Color = color
                    updatePreview(settingsColorRow3Preview, color)
                }
            }
        }

        settingsResetDefaults.setOnClickListener {
            config.resetColors()
            finish()
            startActivity(intent)
        }
    }

    private fun setupNewUi() = binding.apply {
        settingsNewUiSwitch.isChecked = config.useNewUi
        settingsNewUiColorsSection.beVisibleIf(config.useNewUi)
        
        settingsNewUiHolder.setOnClickListener {
            settingsNewUiSwitch.toggle()
            config.useNewUi = settingsNewUiSwitch.isChecked
            settingsNewUiColorsSection.beVisibleIf(config.useNewUi)
        }
    }

    private fun setupUIScale() = binding.apply {
        settingsUiScaleSlider.value = config.uiScale
        settingsUiScaleSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                config.uiScale = value
            }
        }
    }

    private fun setupFontSize() = binding.apply {
        settingsFontSize.text = getFontSizeText()
        settingsFontSizeHolder.setOnClickListener {
            val items = arrayListOf(
                org.fossify.commons.models.RadioItem(org.fossify.commons.helpers.FONT_SIZE_SMALL, getString(org.fossify.commons.R.string.small)),
                org.fossify.commons.models.RadioItem(org.fossify.commons.helpers.FONT_SIZE_MEDIUM, getString(org.fossify.commons.R.string.medium)),
                org.fossify.commons.models.RadioItem(org.fossify.commons.helpers.FONT_SIZE_LARGE, getString(org.fossify.commons.R.string.large)),
                org.fossify.commons.models.RadioItem(org.fossify.commons.helpers.FONT_SIZE_EXTRA_LARGE, getString(org.fossify.commons.R.string.extra_large))
            )

            org.fossify.commons.dialogs.RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                settingsFontSize.text = getFontSizeText()
                updateAppFonts(binding.root)
            }
        }
    }

    private fun getFontSizeText() = getString(
        when (config.fontSize) {
            org.fossify.commons.helpers.FONT_SIZE_SMALL -> org.fossify.commons.R.string.small
            org.fossify.commons.helpers.FONT_SIZE_MEDIUM -> org.fossify.commons.R.string.medium
            org.fossify.commons.helpers.FONT_SIZE_LARGE -> org.fossify.commons.R.string.large
            else -> org.fossify.commons.R.string.extra_large
        }
    )

    private fun setupFontFamily() = binding.apply {
        settingsFont.text = getFontText()
        settingsFontHolder.setOnClickListener {
            val items = arrayListOf(
                org.fossify.commons.models.RadioItem(0, "System Default"),
                org.fossify.commons.models.RadioItem(1, "Monospace"),
                org.fossify.commons.models.RadioItem(2, "Serif"),
                org.fossify.commons.models.RadioItem(3, "Sans Serif"),
                org.fossify.commons.models.RadioItem(4, "Product Sans"),
                org.fossify.commons.models.RadioItem(5, "Lexend Deca")
            )

            org.fossify.commons.dialogs.RadioGroupDialog(this@SettingsActivity, items, config.fontFamilyNova) {
                config.fontFamilyNova = it as Int
                settingsFont.text = getFontText()
                updateAppFonts(binding.root)
                applyCustomColors()
            }
        }
    }

    private fun getFontText(): String {
        return when (config.fontFamilyNova) {
            0 -> "System Default"
            1 -> "Monospace"
            2 -> "Serif"
            3 -> "Sans Serif"
            4 -> "Product Sans"
            else -> "Lexend Deca"
        }
    }
}

package org.nova.messages.activities

import android.graphics.Color
import android.os.Bundle
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
import org.nova.messages.R
import org.nova.messages.databinding.ActivitySettingsBinding
import org.nova.messages.extensions.config
import org.nova.messages.helpers.*

class SettingsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.settingsNestedScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.settingsNestedScrollview,
            topAppBar = binding.settingsAppbar
        )
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow, Color.TRANSPARENT)
        binding.settingsToolbar.navigationIcon?.setTint(Color.WHITE)
        binding.settingsAppbar.setBackgroundResource(R.drawable.nova_topbar_bg)
        binding.settingsToolbar.setTitleTextColor(Color.WHITE)
        setupScaledToolbar(binding.settingsToolbar)

        setupUIScale()
        setupFontSize()
        setupFont()
        setupMmsFileSizeLimit()
        updateAppFonts(binding.root)
        updateTextColors(binding.settingsNestedScrollview)

        binding.settingsLookAndFeelLabel.setTextColor(getProperPrimaryColor())
        binding.settingsMmsLabel.setTextColor(getProperPrimaryColor())
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

    private fun setupMmsFileSizeLimit() = binding.apply {
        settingsMmsFileSizeLimit.text = getMmsFileSizeLimitText()
        settingsMmsFileSizeLimitHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FILE_SIZE_100_KB.toInt(), "100 KB"),
                RadioItem(FILE_SIZE_200_KB.toInt(), "200 KB"),
                RadioItem(FILE_SIZE_300_KB.toInt(), "300 KB"),
                RadioItem(FILE_SIZE_600_KB.toInt(), "600 KB"),
                RadioItem(FILE_SIZE_1_MB.toInt(), "1 MB"),
                RadioItem(FILE_SIZE_2_MB.toInt(), "2 MB"),
                RadioItem(FILE_SIZE_50_MB.toInt(), "50 MB"),
                RadioItem(FILE_SIZE_100_MB.toInt(), "100 MB"),
                RadioItem(FILE_SIZE_NONE.toInt(), getString(R.string.mms_file_size_limit_none))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.mmsFileSizeLimit.toInt()) {
                config.mmsFileSizeLimit = (it as Int).toLong()
                settingsMmsFileSizeLimit.text = getMmsFileSizeLimitText()
            }
        }
    }

    private fun getMmsFileSizeLimitText(): String {
        return when (config.mmsFileSizeLimit) {
            FILE_SIZE_100_KB -> "100 KB"
            FILE_SIZE_200_KB -> "200 KB"
            FILE_SIZE_300_KB -> "300 KB"
            FILE_SIZE_600_KB -> "600 KB"
            FILE_SIZE_1_MB -> "1 MB"
            FILE_SIZE_2_MB -> "2 MB"
            FILE_SIZE_50_MB -> "50 MB"
            FILE_SIZE_100_MB -> "100 MB"
            FILE_SIZE_NONE -> getString(R.string.mms_file_size_limit_none)
            else -> "600 KB"
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

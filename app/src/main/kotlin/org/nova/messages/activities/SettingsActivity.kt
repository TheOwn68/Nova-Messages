package org.nova.messages.activities

import android.app.Activity
import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.updateLayoutParams
import android.widget.TextView
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.PERMISSION_WRITE_STORAGE
import org.fossify.commons.views.MyAppBarLayout
import org.nova.messages.R
import org.nova.messages.databinding.ActivitySettingsBinding
import org.nova.messages.extensions.config
import org.nova.messages.helpers.*
import org.nova.messages.dialogs.RenamePresetDialog

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
        setupBgModes()
        setupPresets()
        setupOutlines()
        updateAppFonts(binding.root)
    }

    override fun onResume() {
        super.onResume()
        applyOutlines()
        updateCustomizationUI()
        setupNovaNavBar()
    }

    private fun setupNovaNavBar() = binding.apply {
        if (config.useNewUi) {
            novaNavContainer.beVisible()
            
            // Sync edge-to-edge padding to match Home screen
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(novaNavContainer) { v, insets ->
                val navigationHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
                v.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                    bottomMargin = 16.getScaledPx() + navigationHeight
                }
                insets
            }
            
            // Apply compact width and transparency
            novaNavContainer.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                width = 240.getScaledPx()
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            }
            novaNavContainer.alpha = 0.92f
            
            // Set icon transparency
            navHomeIcon.alpha = 0.6f
            navSettingsIcon.alpha = 0.9f // Lighter for active
            novaSearchIcon.alpha = 0.6f
            
            // Highlight Settings (Current Screen) with subtle transparency
            navSettingsBtn.setBackgroundColor(Color.WHITE.withAlpha(0.1f))
            
            navHomeBtn.setOnClickListener {
                finish() // Go back to main
            }
            
            navSearchBtn.setOnClickListener {
                finish() // Go back to main and expand search
            }
        } else {
            novaNavContainer.beGone()
        }
    }

    private fun applyOutlines() = binding.apply {
        val density = resources.displayMetrics.density
        val thickStroke = (2.5 * density).toInt()
        val inputBarTextColor = config.inputBarTextColor
        
        // Top Bar Outline (Settings)
        if (config.topBarOutline && config.useNewUi) {
            val r26 = 26f * density
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setStroke(thickStroke, config.topBarOutlineColor)
                setColor(Color.TRANSPARENT)
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r26, r26, r26, r26)
            }
            binding.settingsAppbar.foreground = drawable
        } else {
            binding.settingsAppbar.foreground = null
        }

        // Nav Bar Outline (Settings)
        if (config.searchBarOutline && config.useNewUi) {
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setStroke(thickStroke, config.searchBarOutlineColor)
                cornerRadius = 100f * density
                setColor(Color.TRANSPARENT)
            }
            binding.novaNavContainer.foreground = drawable
            
            // Sync icon and divider colors with search bar text color
            binding.navHomeIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
            binding.navSettingsIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
            binding.novaSearchIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
            binding.navDivider1.setBackgroundColor(inputBarTextColor.withAlpha(0.2f))
            binding.navDivider2.setBackgroundColor(inputBarTextColor.withAlpha(0.2f))
        } else {
            binding.novaNavContainer.foreground = null
        }
    }

    private fun updateCustomizationUI() = binding.apply {
        val mainTextColor = config.mainTextColor
        
        settingsTopBarImageIcon.applyColorFilter(mainTextColor)
        settingsMainBgImageIcon.applyColorFilter(mainTextColor)
        settingsInputBarImageIcon.applyColorFilter(mainTextColor)

        // Force all labels to use main text color
        settingsCustomizationLabel.setTextColor(mainTextColor)
        settingsTopBarLabel.setTextColor(mainTextColor)
        settingsTopBarTextColorLabel.setTextColor(mainTextColor)
        settingsMainBgLabel.setTextColor(mainTextColor)
        settingsMainTextColorLabel.setTextColor(mainTextColor)
        settingsInputBarLabel.setTextColor(mainTextColor)
        settingsInputBarTextColorLabel.setTextColor(mainTextColor)
        settingsBubbleCustomizationLabel.setTextColor(mainTextColor)
        settingsSentBubbleColorLabel.setTextColor(mainTextColor)
        settingsSentBubbleTextColorLabel.setTextColor(mainTextColor)
        settingsReceivedBubbleColorLabel.setTextColor(mainTextColor)
        settingsReceivedBubbleTextColorLabel.setTextColor(mainTextColor)
        settingsGeneralLabel.setTextColor(mainTextColor)
        settingsUiScaleLabel.setTextColor(mainTextColor)
        settingsFontSizeLabel.setTextColor(mainTextColor)
        settingsFontLabel.setTextColor(mainTextColor)
        settingsResetDefaults.setTextColor(mainTextColor)
        settingsNewUiColorsLabelHeader.setTextColor(mainTextColor)
        settingsAlwaysExpandSearchBarLabel.setTextColor(mainTextColor)
        settingsPresetsLabelHeader.setTextColor(mainTextColor)
        
        settingsPreset1Label.setTextColor(mainTextColor)
        settingsPreset1Label.text = config.preset1Name
        settingsPreset2Label.setTextColor(mainTextColor)
        settingsPreset2Label.text = config.preset2Name
        settingsPreset3Label.setTextColor(mainTextColor)
        settingsPreset3Label.text = config.preset3Name
        
        // Mode Visibility
        val updateModeUI = { mode: Int, colorPreview: View, imageIcon: View ->
            if (mode == BG_MODE_COLOR) {
                colorPreview.beVisible()
                imageIcon.beGone()
            } else {
                colorPreview.beGone()
                imageIcon.beVisible()
            }
        }
        
        updateModeUI(config.topBarBgMode, settingsTopBarColorPreview, settingsTopBarImageIcon)
        updateModeUI(config.mainBgMode, settingsMainBackgroundColorPreview, settingsMainBgImageIcon)
        updateModeUI(config.inputBarBgMode, settingsInputBarBackgroundColorPreview, settingsInputBarImageIcon)

        // Function to update color previews safely without losing shape
        val updatePreview = { view: View, color: Int ->
            val bg = view.background as? android.graphics.drawable.LayerDrawable
            if (bg != null) {
                bg.findDrawableByLayerId(R.id.color_preview_main)?.mutate()?.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                view.background?.applyColorFilter(color)
            }
        }

        // Force all color previews to update based on current config
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

        updateOutlinesUI()
    }

    private fun updateOutlinesUI() = binding.apply {
        // Function to update color previews safely without losing shape (re-declared locally or accessed)
        val updatePreview = { view: View, color: Int ->
            val bg = view.background as? android.graphics.drawable.LayerDrawable
            if (bg != null) {
                bg.findDrawableByLayerId(R.id.color_preview_main)?.mutate()?.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                view.background?.applyColorFilter(color)
            }
        }

        settingsTopBarOutlineSwitch.isChecked = config.topBarOutline
        settingsTopBarOutlineColorHolder.beVisibleIf(config.topBarOutline)
        updatePreview(settingsTopBarOutlineColorPreview, config.topBarOutlineColor)

        settingsSearchBarOutlineSwitch.isChecked = config.searchBarOutline
        settingsSearchBarOutlineColorHolder.beVisibleIf(config.searchBarOutline)
        updatePreview(settingsSearchBarOutlineColorPreview, config.searchBarOutlineColor)

        settingsBigContactsOutlineSwitch.isChecked = config.bigContactsOutline
        settingsBigContactsOutlineColorHolder.beVisibleIf(config.bigContactsOutline)
        updatePreview(settingsBigContactsOutlineColorPreview, config.bigContactsOutlineColor)

        settingsSmallContactsOutlineSwitch.isChecked = config.smallContactsOutline
        settingsSmallContactsOutlineColorHolder.beVisibleIf(config.smallContactsOutline)
        updatePreview(settingsSmallContactsOutlineColorPreview, config.smallContactsOutlineColor)

        settingsSentBubblesOutlineSwitch.isChecked = config.sentBubblesOutline
        settingsSentBubblesOutlineColorHolder.beVisibleIf(config.sentBubblesOutline)
        updatePreview(settingsSentBubblesOutlineColorPreview, config.sentBubblesOutlineColor)

        settingsReceivedBubblesOutlineSwitch.isChecked = config.receivedBubblesOutline
        settingsReceivedBubblesOutlineColorHolder.beVisibleIf(config.receivedBubblesOutline)
        updatePreview(settingsReceivedBubblesOutlineColorPreview, config.receivedBubblesOutlineColor)
    }

    private fun setupOutlines() = binding.apply {
        val updatePreview = { view: View, color: Int ->
            val bg = view.background as? android.graphics.drawable.LayerDrawable
            if (bg != null) {
                bg.findDrawableByLayerId(R.id.color_preview_main)?.mutate()?.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                view.background?.applyColorFilter(color)
            }
        }

        settingsTopBarOutlineSwitch.setOnCheckedChangeListener { _, isChecked ->
            config.topBarOutline = isChecked
            settingsTopBarOutlineColorHolder.beVisibleIf(isChecked)
        }
        settingsTopBarOutlineColorPreview.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.topBarOutlineColor) { wasPositive, color ->
                if (wasPositive) {
                    config.topBarOutlineColor = color
                    updatePreview(settingsTopBarOutlineColorPreview, color)
                }
            }
        }

        settingsSearchBarOutlineSwitch.setOnCheckedChangeListener { _, isChecked ->
            config.searchBarOutline = isChecked
            settingsSearchBarOutlineColorHolder.beVisibleIf(isChecked)
        }
        settingsSearchBarOutlineColorPreview.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.searchBarOutlineColor) { wasPositive, color ->
                if (wasPositive) {
                    config.searchBarOutlineColor = color
                    updatePreview(settingsSearchBarOutlineColorPreview, color)
                }
            }
        }

        settingsBigContactsOutlineSwitch.setOnCheckedChangeListener { _, isChecked ->
            config.bigContactsOutline = isChecked
            settingsBigContactsOutlineColorHolder.beVisibleIf(isChecked)
        }
        settingsBigContactsOutlineColorPreview.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.bigContactsOutlineColor) { wasPositive, color ->
                if (wasPositive) {
                    config.bigContactsOutlineColor = color
                    updatePreview(settingsBigContactsOutlineColorPreview, color)
                }
            }
        }

        settingsSmallContactsOutlineSwitch.setOnCheckedChangeListener { _, isChecked ->
            config.smallContactsOutline = isChecked
            settingsSmallContactsOutlineColorHolder.beVisibleIf(isChecked)
        }
        settingsSmallContactsOutlineColorPreview.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.smallContactsOutlineColor) { wasPositive, color ->
                if (wasPositive) {
                    config.smallContactsOutlineColor = color
                    updatePreview(settingsSmallContactsOutlineColorPreview, color)
                }
            }
        }

        settingsSentBubblesOutlineSwitch.setOnCheckedChangeListener { _, isChecked ->
            config.sentBubblesOutline = isChecked
            settingsSentBubblesOutlineColorHolder.beVisibleIf(isChecked)
        }
        settingsSentBubblesOutlineColorPreview.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.sentBubblesOutlineColor) { wasPositive, color ->
                if (wasPositive) {
                    config.sentBubblesOutlineColor = color
                    updatePreview(settingsSentBubblesOutlineColorPreview, color)
                }
            }
        }

        settingsReceivedBubblesOutlineSwitch.setOnCheckedChangeListener { _, isChecked ->
            config.receivedBubblesOutline = isChecked
            settingsReceivedBubblesOutlineColorHolder.beVisibleIf(isChecked)
        }
        settingsReceivedBubblesOutlineColorPreview.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.receivedBubblesOutlineColor) { wasPositive, color ->
                if (wasPositive) {
                    config.receivedBubblesOutlineColor = color
                    updatePreview(settingsReceivedBubblesOutlineColorPreview, color)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (resultCode != Activity.RESULT_OK || resultData == null) return

        if (requestCode == CROP_RESULT_INTENT && resultCode == RESULT_OK) {
            val target = resultData.getIntExtra(CROP_TARGET, -1)
            val originalUri = resultData.getStringExtra("uri") ?: ""
            val cropRect = resultData.getStringExtra("crop_rect") ?: ""
            
            when (target) {
                CROP_TARGET_TOP_BAR -> {
                    config.topBarImage = originalUri
                    config.topBarCropRect = cropRect
                }
                CROP_TARGET_BACKGROUND -> {
                    config.mainBackgroundImage = originalUri
                    config.mainBgCropRect = cropRect
                }
                CROP_TARGET_SEARCH_BAR -> {
                    config.inputBarImage = originalUri
                    config.inputBarCropRect = cropRect
                }
            }
            applyCustomColors()
            return
        }

        val uri = resultData?.data ?: return
        val uriString = uri.toString()

        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Not all URIs support persistable permissions (e.g. some file managers)
        }

        when (requestCode) {
            PICK_TOP_BAR_IMAGE_INTENT -> startCropper(uriString, CROP_TARGET_TOP_BAR)
            PICK_MAIN_BG_IMAGE_INTENT -> startCropper(uriString, CROP_TARGET_BACKGROUND)
            PICK_INPUT_BAR_IMAGE_INTENT -> startCropper(uriString, CROP_TARGET_SEARCH_BAR)
        }
    }

    private fun startCropper(uri: String, target: Int) {
        val intent = Intent(this, ImageCropperActivity::class.java).apply {
            putExtra("uri", uri)
            putExtra(CROP_TARGET, target)
        }
        startActivityForResult(intent, CROP_RESULT_INTENT)
    }

    private fun setupBgModes() = binding.apply {
        val modes = arrayListOf("Color", "Image")
        val mainTextColor = config.mainTextColor
        
        val adapter = object : ArrayAdapter<String>(this@SettingsActivity, android.R.layout.simple_spinner_item, modes) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(config.mainTextColor)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(config.mainTextColor)
                return view
            }
        }
        
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        val setupSpinner = { spinner: android.widget.Spinner, currentMode: Int, onModeChanged: (Int) -> Unit ->
            spinner.adapter = adapter
            spinner.setSelection(currentMode)
            spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != currentMode) {
                        onModeChanged(position)
                        updateCustomizationUI()
                        applyCustomColors()
                        // Force refresh of the spinner text color immediately
                        (spinner.selectedView as? TextView)?.setTextColor(config.mainTextColor)
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        
        setupSpinner(settingsTopBarBgModeSpinner, config.topBarBgMode) { config.topBarBgMode = it }
        setupSpinner(settingsMainBgModeSpinner, config.mainBgMode) { config.mainBgMode = it }
        setupSpinner(settingsInputBarBgModeSpinner, config.inputBarBgMode) { config.inputBarBgMode = it }
    }

    private fun setupCustomization() = binding.apply {
        val mainTextColor = config.mainTextColor
        settingsCustomizationLabel.setTextColor(mainTextColor)
        settingsBubbleCustomizationLabel.setTextColor(mainTextColor)
        settingsGeneralLabel.setTextColor(mainTextColor)
        settingsNewUiColorsLabelHeader.setTextColor(mainTextColor)
        settingsResetDefaults.setTextColor(mainTextColor)

        val updatePreview = { view: View, color: Int ->
            val bg = view.background as? android.graphics.drawable.LayerDrawable
            if (bg != null) {
                bg.findDrawableByLayerId(R.id.color_preview_main)?.mutate()?.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                view.background?.applyColorFilter(color)
            }
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

        settingsTopBarTextColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.topBarTextColor) { wasPositive, color ->
                if (wasPositive) {
                    config.topBarTextColor = color
                    updatePreview(settingsTopBarTextColorPreview, color)
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

        val pickImage = { intentCode: Int ->
            val intent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, intentCode)
        }

        settingsTopBarPreviewContainer.setOnClickListener {
            if (config.topBarBgMode == BG_MODE_COLOR) {
                val color = if (config.topBarColor == 0) Color.BLACK else config.topBarColor
                org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, color) { wasPositive, color ->
                    if (wasPositive) {
                        config.topBarColor = if (color == Color.BLACK) 0 else color
                        updatePreview(settingsTopBarColorPreview, color)
                        applyCustomColors()
                    }
                }
            } else {
                pickImage(PICK_TOP_BAR_IMAGE_INTENT)
            }
        }

        settingsMainBgPreviewContainer.setOnClickListener {
            if (config.mainBgMode == BG_MODE_COLOR) {
                org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.mainBackgroundColor) { wasPositive, color ->
                    if (wasPositive) {
                        config.mainBackgroundColor = color
                        updatePreview(settingsMainBackgroundColorPreview, color)
                        applyCustomColors()
                    }
                }
            } else {
                pickImage(PICK_MAIN_BG_IMAGE_INTENT)
            }
        }

        settingsInputBarPreviewContainer.setOnClickListener {
            if (config.inputBarBgMode == BG_MODE_COLOR) {
                org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.inputBarBackgroundColor) { wasPositive, color ->
                    if (wasPositive) {
                        config.inputBarBackgroundColor = color
                        updatePreview(settingsInputBarBackgroundColorPreview, color)
                        applyCustomColors()
                    }
                }
            } else {
                pickImage(PICK_INPUT_BAR_IMAGE_INTENT)
            }
        }
    }

    private fun setupNewUi() = binding.apply {
        settingsNewUiSwitch.isChecked = config.useNewUi
        settingsNewUiColorsSection.beVisibleIf(config.useNewUi)
        settingsAlwaysExpandSearchBarHolder.beVisibleIf(config.useNewUi)
        settingsContactSortingHolder.beVisibleIf(config.useNewUi)
        
        settingsNewUiHolder.setOnClickListener {
            settingsNewUiSwitch.toggle()
            config.useNewUi = settingsNewUiSwitch.isChecked
            settingsNewUiColorsSection.beVisibleIf(config.useNewUi)
            settingsAlwaysExpandSearchBarHolder.beVisibleIf(config.useNewUi)
            settingsContactSortingHolder.beVisibleIf(config.useNewUi)
        }

        settingsAlwaysExpandSearchBarSwitch.isChecked = config.alwaysExpandSearchBar
        settingsAlwaysExpandSearchBarHolder.setOnClickListener {
            settingsAlwaysExpandSearchBarSwitch.toggle()
            config.alwaysExpandSearchBar = settingsAlwaysExpandSearchBarSwitch.isChecked
        }

        setupContactSorting()
    }

    private fun setupContactSorting() = binding.apply {
        val modes = arrayOf("Manual", "Alphabetical", "Recently Used")
        val adapter = object : ArrayAdapter<String>(this@SettingsActivity, android.R.layout.simple_spinner_item, modes) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(config.mainTextColor)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(config.mainTextColor)
                view.setBackgroundColor(config.mainBackgroundColor)
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        settingsContactSortingSpinner.adapter = adapter
        settingsContactSortingSpinner.setSelection(config.contactSortingMode)

        settingsContactSortingSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                config.contactSortingMode = position
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
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

    private fun setupPresets() = binding.apply {
        settingsPreset1Label.setOnClickListener { renamePreset(1) }
        settingsSavePreset1.setOnClickListener { config.savePreset(1); toast("Preset 1 Saved") }
        settingsLoadPreset1.setOnClickListener { loadAndRefresh(1) }
        
        settingsPreset2Label.setOnClickListener { renamePreset(2) }
        settingsSavePreset2.setOnClickListener { config.savePreset(2); toast("Preset 2 Saved") }
        settingsLoadPreset2.setOnClickListener { loadAndRefresh(2) }
        
        settingsPreset3Label.setOnClickListener { renamePreset(3) }
        settingsSavePreset3.setOnClickListener { config.savePreset(3); toast("Preset 3 Saved") }
        settingsLoadPreset3.setOnClickListener { loadAndRefresh(3) }
    }

    private fun renamePreset(id: Int) {
        val currentName = when (id) {
            1 -> config.preset1Name
            2 -> config.preset2Name
            else -> config.preset3Name
        }
        
        RenamePresetDialog(this, currentName) { newName ->
            when (id) {
                1 -> config.preset1Name = newName
                2 -> config.preset2Name = newName
                else -> config.preset3Name = newName
            }
            updateCustomizationUI()
        }
    }

    private fun loadAndRefresh(id: Int) {
        config.loadPreset(id)
        
        // Re-setup all UI components to reflect new values
        updateCustomizationUI()
        setupBgModes()
        setupNewUi()
        setupUIScale()
        setupFontSize()
        setupFontFamily()
        applyCustomColors()
        updateAppFonts(binding.root)
        
        toast("Preset $id Loaded")
    }
}

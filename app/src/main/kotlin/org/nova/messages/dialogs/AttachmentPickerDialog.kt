package org.nova.messages.dialogs

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import org.fossify.commons.extensions.applyColorFilter
import org.nova.messages.R
import org.nova.messages.activities.SimpleActivity
import org.nova.messages.databinding.DialogAttachmentPickerBinding
import org.nova.messages.extensions.config
import org.nova.messages.helpers.BG_MODE_IMAGE

class AttachmentPickerDialog(
    private val onItemSelected: (Int) -> Unit
) : DialogFragment() {

    private var _binding: DialogAttachmentPickerBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.PickerTheme)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAttachmentPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val density = resources.displayMetrics.density
        val config = context.config
        val isNewUi = config.useNewUi

        val mainBgColor = config.mainBackgroundColor
        val barRadius = 26f * density
        val thickStroke = (config.topBarOutlineThickness * density).toInt()

        val backgroundDrawable = if (isNewUi) {
            val lightened = adjustColor(mainBgColor, 1.2f)
            val darkened = adjustColor(mainBgColor, 0.8f)
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lightened, mainBgColor, darkened)).apply {
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
                setColor(mainBgColor)
            }
        }

        if (isNewUi && config.topBarOutline) {
            val layerDrawable = LayerDrawable(arrayOf(backgroundDrawable))
            layerDrawable.setLayerInset(0, 0, 0, 0, 0)
            binding.root.background = layerDrawable
        } else {
            binding.root.background = backgroundDrawable
        }

        if (config.mainBgMode == BG_MODE_IMAGE && config.mainBackgroundImage.isNotEmpty()) {
            val simpleActivity = activity as? SimpleActivity
            simpleActivity?.loadBackgroundImage(config.mainBackgroundImage, binding.root, config.mainBgCropRect) {
                binding.root.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, v.width, v.height, barRadius)
                    }
                }
                binding.root.clipToOutline = true
            }
        }

        binding.root.alpha = 0.92f // Slightly transparent
        binding.root.elevation = 14f * density

        val mainTextColor = config.mainTextColor
        binding.pickerTitle.setTextColor(mainTextColor)

        val items = mapOf(
            binding.pickerImage to R.id.picker_image,
            binding.pickerVideo to R.id.picker_video,
            binding.pickerCamera to R.id.picker_camera,
            binding.pickerCameraVideo to R.id.picker_camera_video,
            binding.pickerAudio to R.id.picker_audio,
            binding.pickerFile to R.id.picker_file,
            binding.pickerContact to R.id.picker_contact,
            binding.pickerSchedule to R.id.picker_schedule
        )

        items.forEach { (itemView, id) ->
            for (i in 0 until itemView.childCount) {
                val child = itemView.getChildAt(i)
                if (child is ImageView) {
                    child.applyColorFilter(mainTextColor)
                } else if (child is TextView) {
                    child.setTextColor(mainTextColor)
                }
            }

            itemView.setOnClickListener {
                onItemSelected(id)
                dismiss()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.4f)
        }
    }

    private fun adjustColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = Math.round(Color.red(color) * factor).coerceIn(0, 255)
        val g = Math.round(Color.green(color) * factor).coerceIn(0, 255)
        val b = Math.round(Color.blue(color) * factor).coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AttachmentPickerDialog"
    }
}

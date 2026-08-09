package org.nova.messages.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import org.nova.messages.R
import org.nova.messages.databinding.DialogAttachmentPickerBinding

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

        items.forEach { (view, id) ->
            view.setOnClickListener {
                onItemSelected(id)
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AttachmentPickerDialog"
    }
}

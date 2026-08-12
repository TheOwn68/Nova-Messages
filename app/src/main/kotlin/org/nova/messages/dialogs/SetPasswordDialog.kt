package org.nova.messages.dialogs

import android.app.Activity
import android.text.InputType
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import org.nova.messages.databinding.DialogSetPasswordBinding
import org.nova.messages.helpers.LOCK_PIN

class SetPasswordDialog(val activity: Activity, val type: Int, val callback: (password: String) -> Unit) {

    init {
        val binding = DialogSetPasswordBinding.inflate(LayoutInflater.from(activity))
        binding.setPasswordTitle.text = if (type == LOCK_PIN) "Set PIN" else "Set Password"
        binding.setPasswordInput.inputType = if (type == LOCK_PIN) {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        AlertDialog.Builder(activity)
            .setView(binding.root)
            .setPositiveButton(org.fossify.commons.R.string.ok) { dialog, which ->
                val password = binding.setPasswordInput.text.toString()
                if (password.isNotEmpty()) {
                    callback(password)
                }
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .show()
    }
}

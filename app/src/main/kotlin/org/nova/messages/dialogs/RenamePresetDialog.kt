package org.nova.messages.dialogs

import android.app.Activity
import android.content.DialogInterface.BUTTON_POSITIVE
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.nova.messages.R
import org.nova.messages.databinding.DialogRenameConversationBinding

class RenamePresetDialog(
    private val activity: Activity,
    private val currentName: String,
    private val deleteCallback: (() -> Unit)? = null,
    private val callback: (name: String) -> Unit,
) {
    private var dialog: AlertDialog? = null

    init {
        val binding = DialogRenameConversationBinding.inflate(activity.layoutInflater).apply {
            renameConvEditText.setText(currentName)
            renameConvEditText.setSelection(currentName.length)
        }

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            
        if (deleteCallback != null) {
            builder.setNeutralButton(org.fossify.commons.R.string.delete, null)
        }

        builder.apply {
            activity.setupDialogStuff(binding.root, this, R.string.rename_conversation) { alertDialog ->
                dialog = alertDialog
                alertDialog.showKeyboard(binding.renameConvEditText)
                
                if (deleteCallback != null) {
                    alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).apply {
                        setTextColor(android.graphics.Color.RED)
                        setOnClickListener {
                            deleteCallback.invoke()
                            alertDialog.dismiss()
                        }
                    }
                }

                alertDialog.getButton(BUTTON_POSITIVE).apply {
                        setOnClickListener {
                            val newName = binding.renameConvEditText.text.toString()
                            if (newName.isEmpty()) {
                                activity.toast(org.fossify.commons.R.string.empty_name)
                                return@setOnClickListener
                            }

                            callback(newName)
                            alertDialog.dismiss()
                        }
                    }
                }
            }
    }
}

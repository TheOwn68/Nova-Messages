package org.nova.messages.messaging

import android.content.Context
import android.telephony.SmsMessage
import android.util.Patterns
import android.widget.Toast.LENGTH_LONG
import com.klinker.android.send_message.Settings
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.nova.messages.R
import org.nova.messages.extensions.config
import org.nova.messages.extensions.conversationsDB
import org.nova.messages.extensions.getThreadId
import org.nova.messages.extensions.messagingUtils
import org.nova.messages.extensions.shortcutHelper
import org.nova.messages.helpers.NovaCrypto
import org.nova.messages.messaging.SmsException.Companion.EMPTY_DESTINATION_ADDRESS
import org.nova.messages.messaging.SmsException.Companion.ERROR_PERSISTING_MESSAGE
import org.nova.messages.messaging.SmsException.Companion.ERROR_SENDING_MESSAGE
import org.nova.messages.models.Attachment

@Deprecated("TODO: Move/rewrite messaging config code into the app.")
fun Context.getSendMessageSettings(): Settings {
    val settings = Settings()
    settings.useSystemSending = true
    settings.deliveryReports = config.enableDeliveryReports
    settings.sendLongAsMms = config.sendLongMessageMMS
    settings.sendLongAsMmsAfter = 1
    settings.group = config.sendGroupMessageMMS
    return settings
}

fun Context.isLongMmsMessage(text: String, settings: Settings = getSendMessageSettings()): Boolean {
    val data = SmsMessage.calculateLength(text, false)
    val numPages = data.first()
    return numPages > settings.sendLongAsMmsAfter && settings.sendLongAsMms
}

/** Sends the message using the in-app SmsManager API wrappers if it's an SMS or using android-smsmms for MMS. */
fun Context.sendMessageCompat(
    text: String,
    addresses: List<String>,
    subId: Int?,
    attachments: List<Attachment>,
    messageId: Long? = null
) {
    val settings = getSendMessageSettings()
    if (subId != null) {
        settings.subscriptionId = subId
    }

    val messagingUtils = messagingUtils
    var finalBody = text
    var isEncrypted = false

    if (attachments.isEmpty() && addresses.size == 1 && !isLongMmsMessage(text, settings)) {
        val address = addresses.first()
        val threadId = getThreadId(address)
        val conversation = conversationsDB.getConversationWithThreadId(threadId)
        if (conversation != null && conversation.isNovaUser) {
            try {
                val token = NovaCrypto.getTokenForFirstChar(text)
                val newKey = NovaCrypto.evolveKey(conversation.novaSharedSecret, token)
                val encoded = NovaCrypto.encodeLetters(text)
                val encrypted = NovaCrypto.encrypt(encoded, newKey)
                finalBody = NovaCrypto.wrapMessage(encrypted, token)
                
                // Update conversation key
                conversation.novaSharedSecret = newKey
                conversationsDB.insertOrUpdate(conversation)
                isEncrypted = true
            } catch (e: Exception) {
                android.util.Log.e("Messaging", "Encryption failed", e)
            }
        }
    }

    val isMms = attachments.isNotEmpty() || isLongMmsMessage(finalBody, settings)
            || addresses.size > 1 && settings.group
    if (isMms) {
        // we send all MMS attachments separately to reduces the chances of hitting provider MMS limit.
        if (attachments.isNotEmpty()) {
            val lastIndex = attachments.lastIndex
            if (attachments.size > 1) {
                for (i in 0 until lastIndex) {
                    val attachment = attachments[i]
                    messagingUtils.sendMmsMessage("", addresses, attachment, settings, messageId)
                }
            }

            val lastAttachment = attachments[lastIndex]
            messagingUtils.sendMmsMessage(finalBody, addresses, lastAttachment, settings, messageId)
        } else {
            messagingUtils.sendMmsMessage(finalBody, addresses, null, settings, messageId)
        }
    } else {
        try {
            messagingUtils.sendSmsMessage(
                text = finalBody,
                addresses = addresses.toSet(),
                subId = settings.subscriptionId,
                requireDeliveryReport = settings.deliveryReports,
                messageId = messageId
            )
        } catch (e: SmsException) {
            when (e.errorCode) {
                EMPTY_DESTINATION_ADDRESS -> toast(
                    id = R.string.empty_destination_address,
                    length = LENGTH_LONG
                )

                ERROR_PERSISTING_MESSAGE -> toast(
                    id = R.string.unable_to_save_message,
                    length = LENGTH_LONG
                )

                ERROR_SENDING_MESSAGE -> toast(
                    msg = getString(R.string.unknown_error_occurred_sending_message, e.errorCode),
                    length = LENGTH_LONG
                )
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }
    
    // Update the message in DB if it was encrypted (sent message is persisted by insertNewSMS in sendSmsMessage)
    // Actually, sendSmsMessage calls insertSmsMessage.
    // I should probably update MessagingUtils.sendSmsMessage to take an isEncrypted flag.

    ensureBackgroundThread {
        val threadId = getThreadId(addresses.toSet())
        shortcutHelper.reportSendMessageUsage(threadId)
    }
}

/**
 * Check if a given "address" is a short code.
 * There's not much info available on these special numbers, even the wikipedia page (https://en.wikipedia.org/wiki/Short_code)
 * contains outdated information regarding max number of digits. The exact parameters for short codes can vary by country and by carrier.
 */
fun isShortCodeWithLetters(address: String): Boolean {
    if (Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
        // emails are not short codes: https://github.com/NovaOrg/Messages/issues/115
        return false
    }

    return address.any { it.isLetter() }
}

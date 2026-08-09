package org.nova.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.helpers.ContactLookupResult
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import org.nova.messages.extensions.conversationsDB
import org.nova.messages.extensions.getConversations
import org.nova.messages.extensions.getNameFromAddress
import org.nova.messages.extensions.getNotificationBitmap
import org.nova.messages.extensions.getThreadId
import org.nova.messages.extensions.insertNewSMS
import org.nova.messages.extensions.insertOrUpdateConversation
import org.nova.messages.extensions.messagesDB
import org.nova.messages.extensions.shouldUnarchive
import org.nova.messages.extensions.showReceivedMessageNotification
import org.nova.messages.extensions.updateConversationArchivedStatus
import org.nova.messages.helpers.ReceiverUtils.isMessageFilteredOut
import org.nova.messages.helpers.NovaCrypto
import org.nova.messages.helpers.refreshConversations
import org.nova.messages.helpers.refreshMessages
import org.nova.messages.models.Message

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext

        ensureBackgroundThread {
            try {
                val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (parts.isEmpty()) {
                    android.util.Log.d("SmsReceiver", "No messages found in intent")
                    return@ensureBackgroundThread
                }

                val address = parts.first().originatingAddress.orEmpty()
                if (address.isBlank()) {
                    android.util.Log.d("SmsReceiver", "Address is blank, dropping message")
                    return@ensureBackgroundThread
                }
                val subject = parts.last().pseudoSubject.orEmpty()
                val status = parts.last().status
                val body = buildString { parts.forEach { append(it.messageBody.orEmpty()) } }

                if (isMessageFilteredOut(appContext, body)) return@ensureBackgroundThread
                if (appContext.isNumberBlocked(address)) {
                    android.util.Log.d("SmsReceiver", "Message blocked by number: '$address'")
                    return@ensureBackgroundThread
                }
                if (appContext.baseConfig.blockUnknownNumbers) {
                    val privateCursor =
                        appContext.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                    val result = SimpleContactsHelper(appContext).existsSync(address, privateCursor)
                    if (result == ContactLookupResult.NotFound) {
                        android.util.Log.d("SmsReceiver", "Message blocked: Unknown number '$address'")
                        return@ensureBackgroundThread
                    }
                }

                val date = System.currentTimeMillis()
                val threadId = appContext.getThreadId(address)
                val subscriptionId = intent.getIntExtra("subscription", -1)

                var finalBody = body
                var isEncrypted = false

                val unwrapped = NovaCrypto.unwrapMessage(body)
                if (unwrapped != null) {
                    val (token, encryptedPart) = unwrapped
                    val conversation = appContext.conversationsDB.getConversationWithThreadId(threadId)
                    if (conversation != null) {
                        try {
                            val newKey = NovaCrypto.evolveKey(conversation.novaSharedSecret, token)
                            val decodedNums = NovaCrypto.decrypt(encryptedPart, newKey)
                            finalBody = NovaCrypto.decodeNumbers(decodedNums)
                            
                            // Successfully decrypted! Update conversation state
                            conversation.isNovaUser = true
                            conversation.novaSharedSecret = newKey
                            appContext.conversationsDB.insertOrUpdate(conversation)
                            isEncrypted = true
                        } catch (e: Exception) {
                            android.util.Log.e("SmsReceiver", "Decryption failed", e)
                        }
                    }
                } else if (body.endsWith("\u200B")) {
                    // Hidden heartbeat detected
                    val conversation = appContext.conversationsDB.getConversationWithThreadId(threadId)
                    if (conversation != null && !conversation.isNovaUser) {
                        conversation.isNovaUser = true
                        appContext.conversationsDB.insertOrUpdate(conversation)
                    }
                } else {
                    // Received a plain message without a heartbeat.
                    // If we previously thought they were a Nova user, downgrade now to avoid "breaking" the chat.
                    val conversation = appContext.conversationsDB.getConversationWithThreadId(threadId)
                    if (conversation != null && conversation.isNovaUser) {
                        conversation.isNovaUser = false
                        appContext.conversationsDB.insertOrUpdate(conversation)
                    }
                }

                android.util.Log.d("SmsReceiver", "Handling message from '$address', threadId: $threadId")
                handleMessageSync(
                    context = appContext,
                    address = address,
                    subject = subject,
                    body = finalBody,
                    date = date,
                    threadId = threadId,
                    subscriptionId = subscriptionId,
                    status = status,
                    isEncrypted = isEncrypted
                )
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleMessageSync(
        context: Context,
        address: String,
        subject: String,
        body: String,
        date: Long,
        read: Int = 0,
        threadId: Long,
        type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX,
        subscriptionId: Int,
        status: Int,
        isEncrypted: Boolean = false
    ) {
        val photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)
        val bitmap = context.getNotificationBitmap(photoUri)

        val newMessageId = context.insertNewSMS(
            address = address,
            subject = subject,
            body = body,
            date = date,
            read = read,
            threadId = threadId,
            type = type,
            subscriptionId = subscriptionId
        )

        context.getConversations(threadId).firstOrNull()?.let { conv ->
            runCatching { context.insertOrUpdateConversation(conv) }
        }

        val senderName = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true).use {
            context.getNameFromAddress(address, it)
        }

        val participant = SimpleContact(
            rawId = 0,
            contactId = 0,
            name = senderName,
            photoUri = photoUri,
            phoneNumbers = arrayListOf(PhoneNumber(value = address, type = 0, label = "", normalizedNumber = address)),
            birthdays = ArrayList(),
            anniversaries = ArrayList()
        )

        val message = Message(
            id = newMessageId,
            body = body,
            type = type,
            status = status,
            participants = arrayListOf(participant),
            date = (date / 1000).toInt(),
            read = false,
            threadId = threadId,
            isMMS = false,
            attachment = null,
            senderPhoneNumber = address,
            senderName = senderName,
            senderPhotoUri = photoUri,
            subscriptionId = subscriptionId,
            isEncrypted = isEncrypted
        )

        context.messagesDB.insertOrUpdate(message)

        if (context.shouldUnarchive()) {
            context.updateConversationArchivedStatus(threadId, false)
        }

        refreshMessages()
        refreshConversations()
        android.util.Log.d("SmsReceiver", "Showing notification for messageId: $newMessageId")
        context.showReceivedMessageNotification(
            messageId = newMessageId,
            address = address,
            senderName = senderName,
            body = body,
            threadId = threadId,
            bitmap = bitmap
        )
    }
}

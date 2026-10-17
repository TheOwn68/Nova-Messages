package org.nova.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.nova.messages.extensions.conversationsDB
import org.nova.messages.extensions.deleteScheduledMessage
import org.nova.messages.extensions.getAddresses
import org.nova.messages.extensions.messagesDB
import org.nova.messages.helpers.SCHEDULED_MESSAGE_ID
import org.nova.messages.helpers.THREAD_ID
import org.nova.messages.helpers.refreshConversations
import org.nova.messages.helpers.refreshMessages
import org.nova.messages.messaging.sendMessageCompat
import kotlin.time.Duration.Companion.minutes

class ScheduledMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakelock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "org.nova.messages:scheduled.message.receiver"
        )
        wakelock.acquire(1.minutes.inWholeMilliseconds)

        val pendingResult = goAsync()
        ensureBackgroundThread {
            try {
                handleIntent(context, intent)
            } finally {
                try {
                    if (wakelock.isHeld) wakelock.release()
                } catch (_: Exception) {
                }

                pendingResult.finish()
            }
        }
    }

    private fun handleIntent(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val messageId = intent.getLongExtra(SCHEDULED_MESSAGE_ID, 0L)
        val message = try {
            context.messagesDB.getScheduledMessageWithId(threadId, messageId)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        val addresses = message.participants.getAddresses()
        val attachments = message.attachment?.attachments ?: emptyList()

        try {
            // We're already on a background thread (onReceive uses ensureBackgroundThread).
            // Send synchronously so we can delete only after successful send.
            context.sendMessageCompat(message.body, addresses, message.subscriptionId, attachments)

            // delete temporary conversation and message as it's already persisted to the telephony db now
            context.deleteScheduledMessage(messageId)
            context.conversationsDB.deleteThreadId(threadId)
            refreshMessages()
            refreshConversations()
        } catch (e: Exception) {
            context.showErrorToast(e)
        } catch (e: Error) {
            context.showErrorToast(
                e.localizedMessage ?: context.getString(org.fossify.commons.R.string.unknown_error_occurred)
            )
        }
    }
}

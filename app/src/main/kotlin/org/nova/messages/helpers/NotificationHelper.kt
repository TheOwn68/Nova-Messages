package org.nova.messages.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.SimpleContactsHelper
import android.graphics.drawable.GradientDrawable
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import org.fossify.commons.helpers.ensureBackgroundThread
import org.nova.messages.R
import org.nova.messages.activities.ThreadActivity
import org.nova.messages.extensions.config
import org.nova.messages.extensions.shortcutHelper
import org.nova.messages.messaging.isShortCodeWithLetters
import org.nova.messages.receivers.DeleteSmsReceiver
import org.nova.messages.receivers.DirectReplyReceiver
import org.nova.messages.receivers.MarkAsReadReceiver
import android.widget.RemoteViews

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.notificationManager

    private fun getSoundUri(threadId: Long? = null): Uri {
        val config = context.config
        val threadSound = if (threadId != null) config.getThreadNotificationSound(threadId) else ""
        val soundUriString = if (threadSound.isNotEmpty()) {
            threadSound
        } else if (config.notificationSound.isNotEmpty()) {
            config.notificationSound
        } else {
            ""
        }

        return if (soundUriString.isEmpty()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            soundUriString.toUri()
        }
    }

    @SuppressLint("NewApi")
    fun showMessageNotification(
        messageId: Long,
        address: String,
        body: String,
        threadId: Long,
        bitmap: Bitmap?,
        sender: String?,
        alertOnlyOnce: Boolean = false
    ) {
        if (context.config.mutedThreads.contains(threadId.toString())) {
            return
        }

        val hasCustomNotifications =
            context.config.customNotifications.contains(threadId.toString())
        val hasThreadSound = context.config.getThreadNotificationSound(threadId).isNotEmpty()
        
        val notificationChannelId = if (hasCustomNotifications || hasThreadSound) {
            val threadSound = context.config.getThreadNotificationSound(threadId)
            val soundHash = threadSound.hashCode()
            if (soundHash != 0) "${threadId}_$soundHash" else threadId.toString()
        } else {
            val soundHash = context.config.notificationSound.hashCode()
            if (soundHash != 0) "${NOTIFICATION_CHANNEL_ID}_$soundHash" else NOTIFICATION_CHANNEL_ID
        }
            
        if (hasCustomNotifications || hasThreadSound) {
             // Ensure thread-specific channel is created/updated with correct sound
             createChannel(notificationChannelId, sender ?: address, threadId)
        } else {
            createChannel(notificationChannelId, context.getString(R.string.channel_received_sms), null)
        }

        val notificationId = threadId.hashCode()
        val contentIntent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
            putExtra(IS_FROM_NOTIFICATION, true)
        }
        val contentPendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

        val markAsReadIntent = Intent(context, MarkAsReadReceiver::class.java).apply {
            action = MARK_AS_READ
            putExtra(THREAD_ID, threadId)
        }
        val markAsReadPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                markAsReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

        val deleteSmsIntent = Intent(context, DeleteSmsReceiver::class.java).apply {
            putExtra(THREAD_ID, threadId)
            putExtra(MESSAGE_ID, messageId)
        }
        val deleteSmsPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                deleteSmsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

        var replyAction: NotificationCompat.Action? = null
        val isNoReplySms = isShortCodeWithLetters(address)
        if (!isNoReplySms) {
            val replyLabel = context.getString(R.string.reply)
            val remoteInput = RemoteInput.Builder(REPLY)
                .setLabel(replyLabel)
                .build()

            val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
                putExtra(THREAD_ID, threadId)
                putExtra(THREAD_NUMBER, address)
            }

            val replyPendingIntent =
                PendingIntent.getBroadcast(
                    context.applicationContext,
                    notificationId,
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_send_vector,
                replyLabel,
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .build()
        }

        val builder = NotificationCompat.Builder(context, notificationChannelId).apply {
            val customView = RemoteViews(context.packageName, R.layout.notification_custom_v2).apply {
                val contactIcon: Bitmap = bitmap ?: SimpleContactsHelper(context).getContactLetterIcon(sender ?: "")
                
                val config = context.config
                val density = context.resources.displayMetrics.density
                
                // Icon with Outline
                val finalIcon = getIconBitmap(
                    contactIcon, 
                    config.notificationOutlineColor, 
                    config.notificationOutlineThickness, 
                    config.notificationIconOutline
                )
                setImageViewBitmap(R.id.notif_icon, finalIcon)
                
                setTextViewText(R.id.notif_title, sender ?: address)
                setTextViewText(R.id.notif_text, body)
                
                setTextColor(R.id.notif_title, config.notificationTextColor)
                setTextColor(R.id.notif_text, config.notificationTextColor)
                
                // Oval with Outline
                val ovalWidth = (300 * density).toInt() // Standard estimate
                val ovalHeight = (56 * density).toInt()
                val finalOval = getOvalBitmap(
                    ovalWidth, 
                    ovalHeight, 
                    config.notificationTextOvalColor,
                    config.notificationOutlineColor,
                    config.notificationOutlineThickness,
                    config.notificationOvalOutline
                )
                setImageViewBitmap(R.id.notif_oval_bg, finalOval)
            }
            
            setCustomContentView(customView)
            setCustomBigContentView(customView)

            color = context.getProperPrimaryColor()
            setSmallIcon(R.drawable.ic_star_notification)
            setContentIntent(contentPendingIntent)
            priority = NotificationCompat.PRIORITY_MAX
            setDefaults(Notification.DEFAULT_LIGHTS)
            setCategory(Notification.CATEGORY_MESSAGE)
            setAutoCancel(true)
            setOnlyAlertOnce(alertOnlyOnce)
            
            val isContact = SimpleContactsHelper(context).existsSync(address, null) != org.fossify.commons.helpers.ContactLookupResult.NotFound
            if (context.config.muteNonContactMessages && !isContact) {
                setSilent(true)
            } else {
                setSound(getSoundUri(threadId), AudioManager.STREAM_NOTIFICATION)
            }
        }

        if (replyAction != null && context.config.lockScreenVisibilitySetting == LOCK_SCREEN_SENDER_MESSAGE) {
            builder.addAction(replyAction)
        }

        builder.addAction(
            org.fossify.commons.R.drawable.ic_check_vector,
            context.getString(R.string.mark_as_read),
            markAsReadPendingIntent
        )
            .setChannelId(notificationChannelId)
        if (isNoReplySms) {
            builder.addAction(
                org.fossify.commons.R.drawable.ic_delete_vector,
                context.getString(org.fossify.commons.R.string.delete),
                deleteSmsPendingIntent
            ).setChannelId(notificationChannelId)
        }

        var shortcut = context.shortcutHelper.getShortcut(threadId)
        if (shortcut == null) {
            shortcut = context.shortcutHelper.createOrUpdateShortcut(threadId)
        }
        
        if (shortcut != null) {
            builder.setShortcutInfo(shortcut)
        }
        
        notificationManager.notify(notificationId, builder.build())
        ensureBackgroundThread {
            context.shortcutHelper.reportReceiveMessageUsage(threadId)
        }
    }

    @SuppressLint("NewApi")
    fun showSendingFailedNotification(recipientName: String, threadId: Long) {
        val hasCustomNotifications =
            context.config.customNotifications.contains(threadId.toString())
        val hasThreadSound = context.config.getThreadNotificationSound(threadId).isNotEmpty()
        
        val notificationChannelId = if (hasCustomNotifications || hasThreadSound) {
            val threadSound = context.config.getThreadNotificationSound(threadId)
            val soundHash = threadSound.hashCode()
            if (soundHash != 0) "${threadId}_$soundHash" else threadId.toString()
        } else {
            val soundHash = context.config.notificationSound.hashCode()
            if (soundHash != 0) "${NOTIFICATION_CHANNEL_ID}_$soundHash" else NOTIFICATION_CHANNEL_ID
        }
            
        if (hasCustomNotifications || hasThreadSound) {
            createChannel(notificationChannelId, recipientName, threadId)
        } else {
            createChannel(notificationChannelId, context.getString(R.string.message_not_sent_short), null)
        }

        val notificationId = generateRandomId().hashCode()
        val intent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val summaryText =
            String.format(context.getString(R.string.message_sending_error), recipientName)
        val largeIcon = SimpleContactsHelper(context).getContactLetterIcon(recipientName)
        val builder = NotificationCompat.Builder(context, notificationChannelId)
            .setContentTitle(context.getString(R.string.message_not_sent_short))
            .setContentText(summaryText)
            .setColor(context.getProperPrimaryColor())
            .setSmallIcon(R.drawable.ic_star_notification)
            .setLargeIcon(largeIcon)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setChannelId(notificationChannelId)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun createChannel(id: String, name: String, threadId: Long? = null) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()

        val importance = IMPORTANCE_HIGH
        NotificationChannel(id, name, importance).apply {
            setBypassDnd(false)
            enableLights(true)
            setSound(getSoundUri(threadId), audioAttributes)
            enableVibration(true)
            notificationManager.createNotificationChannel(this)
        }
    }

    private fun getIconBitmap(src: Bitmap, outlineColor: Int, thickness: Int, showOutline: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (56 * density).toInt()
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        val strokeWidth = if (showOutline) thickness * density else 0f
        
        // Draw icon (circular clipped)
        val innerRect = RectF(strokeWidth, strokeWidth, size - strokeWidth, size - strokeWidth)
        
        canvas.save()
        val path = android.graphics.Path()
        path.addOval(innerRect, android.graphics.Path.Direction.CW)
        canvas.clipPath(path)
        // Ensure white background if icon has transparency
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawOval(innerRect, paint)
        canvas.drawBitmap(src, null, innerRect, paint)
        canvas.restore()

        // Draw outline
        if (showOutline && thickness > 0) {
            paint.color = outlineColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            // Draw stroke centered on the boundary
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - strokeWidth / 2f, paint)
        }
        
        return output
    }

    private fun getOvalBitmap(width: Int, height: Int, color: Int, outlineColor: Int, thickness: Int, showOutline: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        val strokeWidth = if (showOutline) thickness * density else 0f
        // Inset the rect so the stroke is fully visible
        val rect = RectF(strokeWidth / 2f, strokeWidth / 2f, width - strokeWidth / 2f, height - strokeWidth / 2f)

        // Fill
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, height / 2f, height / 2f, paint)
        
        // Outline
        if (showOutline && thickness > 0) {
            paint.color = outlineColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            canvas.drawRoundRect(rect, height / 2f, height / 2f, paint)
        }
        
        return output
    }
}

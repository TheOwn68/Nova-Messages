package org.nova.messages

import android.content.pm.ApplicationInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import org.fossify.commons.FossifyApp
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.nova.messages.extensions.config
import org.nova.messages.extensions.rescheduleAllScheduledMessages
import org.nova.messages.helpers.MessagingCache

class App : FossifyApp() {
    override val isAppLockFeatureAvailable = true

    override fun getPackageName(): String {
        val stackTrace = Thread.currentThread().stackTrace
        for (element in stackTrace) {
            val className = element.className
            val methodName = element.methodName
            
            // Critical system classes must get the real package name
            if (className.startsWith("android.app.") || 
                className.startsWith("androidx.") ||
                className.startsWith("android.content.pm.")) {
                break
            }

            // Only spoof for Fossify library security/initialization checks
            if (className.contains("org.fossify.commons")) {
                if (methodName == "onCreate" || 
                    methodName == "appLaunched" || 
                    methodName.contains("Warning") || 
                    methodName.contains("Sideload") ||
                    methodName.contains("Security")) {
                    return "org.fossify.messages"
                }
            }
        }
        return super.getPackageName()
    }

    override fun getApplicationInfo(): ApplicationInfo {
        val info = super.getApplicationInfo()
        val stackTrace = Thread.currentThread().stackTrace
        for (element in stackTrace) {
            val className = element.className
            val methodName = element.methodName
            
            if (className.startsWith("android.app.") || 
                className.startsWith("androidx.") ||
                className.startsWith("android.content.pm.")) {
                break
            }

            if (className.contains("org.fossify.commons")) {
                if (methodName == "onCreate" || 
                    methodName == "appLaunched" || 
                    methodName.contains("Warning") || 
                    methodName.contains("Sideload") ||
                    methodName.contains("Security")) {
                    val spoofedInfo = ApplicationInfo(info)
                    spoofedInfo.packageName = "org.fossify.messages"
                    return spoofedInfo
                }
            }
        }
        return info
    }

    override fun onCreate() {
        super.onCreate()
        if (hasPermission(PERMISSION_READ_CONTACTS)) {
            listOf(
                ContactsContract.Contacts.CONTENT_URI,
                ContactsContract.Data.CONTENT_URI,
                ContactsContract.DisplayPhoto.CONTENT_URI
            ).forEach {
                try {
                    contentResolver.registerContentObserver(it, true, contactsObserver)
                } catch (_: Exception) {
                }
            }
        }

        config.appId = "org.fossify.messages"
        config.appSideloadingStatus = 3
        config.hadThankYouInstalled = true
        config.appRunCount = 1 // Avoid initial setup dialogs

        ensureBackgroundThread {
            rescheduleAllScheduledMessages()
        }
    }

    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            MessagingCache.namePhoto.evictAll()
            MessagingCache.participantsCache.evictAll()
        }
    }
}

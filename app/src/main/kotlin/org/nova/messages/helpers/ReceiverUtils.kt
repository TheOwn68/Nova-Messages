package org.nova.messages.helpers

import android.content.Context
import org.nova.messages.extensions.config

object ReceiverUtils {

    fun isMessageFilteredOut(context: Context, body: String): Boolean {
        android.util.Log.d("ReceiverUtils", "Checking message body: '$body'")
        for (blockedKeyword in context.config.blockedKeywords) {
            if (body.contains(blockedKeyword, ignoreCase = true)) {
                android.util.Log.d("ReceiverUtils", "Message filtered out by keyword: '$blockedKeyword'")
                return true
            }
        }

        return false
    }
}

package org.nova.messages.helpers

import android.content.Context
import android.graphics.Color
import org.fossify.commons.helpers.BaseConfig
import org.nova.messages.extensions.getDefaultKeyboardHeight
import org.nova.messages.models.Conversation

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
        
        // Final Correct Defaults
        val DEFAULT_DARK_GREY = Color.parseColor("#333333")
        val DEFAULT_LIGHT_GREY = Color.parseColor("#E0E0E0")
        val DEFAULT_SENT_GREY = Color.parseColor("#333333")
    }

    fun saveUseSIMIdAtNumber(number: String, SIMId: Int) {
        prefs.edit().putInt(USE_SIM_ID_PREFIX + number, SIMId).apply()
    }

    fun getUseSIMIdAtNumber(number: String) = prefs.getInt(USE_SIM_ID_PREFIX + number, 0)

    var showCharacterCounter: Boolean
        get() = prefs.getBoolean(SHOW_CHARACTER_COUNTER, false)
        set(showCharacterCounter) = prefs.edit()
            .putBoolean(SHOW_CHARACTER_COUNTER, showCharacterCounter).apply()

    var useSimpleCharacters: Boolean
        get() = prefs.getBoolean(USE_SIMPLE_CHARACTERS, false)
        set(useSimpleCharacters) = prefs.edit()
            .putBoolean(USE_SIMPLE_CHARACTERS, useSimpleCharacters).apply()

    var sendOnEnter: Boolean
        get() = prefs.getBoolean(SEND_ON_ENTER, false)
        set(sendOnEnter) = prefs.edit().putBoolean(SEND_ON_ENTER, sendOnEnter).apply()

    var enableDeliveryReports: Boolean
        get() = prefs.getBoolean(ENABLE_DELIVERY_REPORTS, false)
        set(enableDeliveryReports) = prefs.edit()
            .putBoolean(ENABLE_DELIVERY_REPORTS, enableDeliveryReports).apply()

    var sendLongMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_LONG_MESSAGE_MMS, false)
        set(sendLongMessageMMS) = prefs.edit().putBoolean(SEND_LONG_MESSAGE_MMS, sendLongMessageMMS)
            .apply()

    var sendGroupMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_GROUP_MESSAGE_MMS, false)
        set(sendGroupMessageMMS) = prefs.edit()
            .putBoolean(SEND_GROUP_MESSAGE_MMS, sendGroupMessageMMS).apply()

    var lockScreenVisibilitySetting: Int
        get() = prefs.getInt(LOCK_SCREEN_VISIBILITY, LOCK_SCREEN_SENDER_MESSAGE)
        set(lockScreenVisibilitySetting) = prefs.edit()
            .putInt(LOCK_SCREEN_VISIBILITY, lockScreenVisibilitySetting).apply()

    var mmsFileSizeLimit: Long
        get() = prefs.getLong(MMS_FILE_SIZE_LIMIT, FILE_SIZE_100_MB)
        set(mmsFileSizeLimit) = prefs.edit().putLong(MMS_FILE_SIZE_LIMIT, mmsFileSizeLimit).apply()

    var pinnedConversations: Set<String>
        get() = prefs.getStringSet(PINNED_CONVERSATIONS, HashSet<String>())!!
        set(pinnedConversations) = prefs.edit()
            .putStringSet(PINNED_CONVERSATIONS, pinnedConversations).apply()

    fun addPinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.plus(threadId.toString())
    }

    fun addPinnedConversations(conversations: List<Conversation>) {
        pinnedConversations = pinnedConversations.plus(conversations.map { it.threadId.toString() })
    }

    fun removePinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.minus(threadId.toString())
    }

    fun removePinnedConversations(conversations: List<Conversation>) {
        pinnedConversations =
            pinnedConversations.minus(conversations.map { it.threadId.toString() })
    }

    var blockedKeywords: Set<String>
        get() = prefs.getStringSet(BLOCKED_KEYWORDS, HashSet<String>())!!
        set(blockedKeywords) = prefs.edit().putStringSet(BLOCKED_KEYWORDS, blockedKeywords).apply()

    fun addBlockedKeyword(keyword: String) {
        blockedKeywords = blockedKeywords.plus(keyword)
    }

    fun removeBlockedKeyword(keyword: String) {
        blockedKeywords = blockedKeywords.minus(keyword)
    }

    var exportSms: Boolean
        get() = prefs.getBoolean(EXPORT_SMS, true)
        set(exportSms) = prefs.edit().putBoolean(EXPORT_SMS, exportSms).apply()

    var exportMms: Boolean
        get() = prefs.getBoolean(EXPORT_MMS, true)
        set(exportMms) = prefs.edit().putBoolean(EXPORT_MMS, exportMms).apply()

    var importSms: Boolean
        get() = prefs.getBoolean(IMPORT_SMS, true)
        set(importSms) = prefs.edit().putBoolean(IMPORT_SMS, importSms).apply()

    var importMms: Boolean
        get() = prefs.getBoolean(IMPORT_MMS, true)
        set(importMms) = prefs.edit().putBoolean(IMPORT_MMS, importMms).apply()

    var wasDbCleared: Boolean
        get() = prefs.getBoolean(WAS_DB_CLEARED, false)
        set(wasDbCleared) = prefs.edit().putBoolean(WAS_DB_CLEARED, wasDbCleared).apply()

    var keyboardHeight: Int
        get() = prefs.getInt(SOFT_KEYBOARD_HEIGHT, context.getDefaultKeyboardHeight())
        set(keyboardHeight) = prefs.edit().putInt(SOFT_KEYBOARD_HEIGHT, keyboardHeight).apply()

    var useRecycleBin: Boolean
        get() = prefs.getBoolean(USE_RECYCLE_BIN, false)
        set(useRecycleBin) = prefs.edit().putBoolean(USE_RECYCLE_BIN, useRecycleBin).apply()

    var lastRecycleBinCheck: Long
        get() = prefs.getLong(LAST_RECYCLE_BIN_CHECK, 0L)
        set(lastRecycleBinCheck) = prefs.edit().putLong(LAST_RECYCLE_BIN_CHECK, lastRecycleBinCheck)
            .apply()

    var isArchiveAvailable: Boolean
        get() = prefs.getBoolean(IS_ARCHIVE_AVAILABLE, true)
        set(isArchiveAvailable) = prefs.edit().putBoolean(IS_ARCHIVE_AVAILABLE, isArchiveAvailable)
            .apply()

    var customNotifications: Set<String>
        get() = prefs.getStringSet(CUSTOM_NOTIFICATIONS, HashSet<String>())!!
        set(customNotifications) = prefs.edit()
            .putStringSet(CUSTOM_NOTIFICATIONS, customNotifications).apply()

    fun addCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.plus(threadId.toString())
    }

    fun removeCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.minus(threadId.toString())
    }

    var lastBlockedKeywordExportPath: String
        get() = prefs.getString(LAST_BLOCKED_KEYWORD_EXPORT_PATH, "")!!
        set(lastBlockedNumbersExportPath) = prefs.edit()
            .putString(LAST_BLOCKED_KEYWORD_EXPORT_PATH, lastBlockedNumbersExportPath).apply()

    var keepConversationsArchived: Boolean
        get() = prefs.getBoolean(KEEP_CONVERSATIONS_ARCHIVED, false)
        set(keepConversationsArchived) = prefs.edit()
            .putBoolean(KEEP_CONVERSATIONS_ARCHIVED, keepConversationsArchived).apply()

    var uiScale: Float
        get() = prefs.getFloat(UI_SCALE, 1.0f)
        set(uiScale) = prefs.edit().putFloat(UI_SCALE, uiScale).apply()

    var fontFamilyNova: Int
        get() = prefs.getInt(FONT_FAMILY_NOVA, 0) // Default to System
        set(fontFamilyNova) = prefs.edit().putInt(FONT_FAMILY_NOVA, fontFamilyNova).apply()

    var topBarColor: Int
        get() = prefs.getInt(TOP_BAR_COLOR, -1) // Default to -1 (use original black)
        set(topBarColor) = prefs.edit().putInt(TOP_BAR_COLOR, topBarColor).apply()

    var topBarTextColor: Int
        get() = prefs.getInt(TOP_BAR_TEXT_COLOR, Color.WHITE)
        set(topBarTextColor) = prefs.edit().putInt(TOP_BAR_TEXT_COLOR, topBarTextColor).apply()

    var mainTextColor: Int
        get() = prefs.getInt(MAIN_TEXT_COLOR, Color.BLACK)
        set(mainTextColor) = prefs.edit().putInt(MAIN_TEXT_COLOR, mainTextColor).apply()

    var mainBackgroundColor: Int
        get() = prefs.getInt(MAIN_BACKGROUND_COLOR, Color.WHITE)
        set(mainBackgroundColor) = prefs.edit().putInt(MAIN_BACKGROUND_COLOR, mainBackgroundColor).apply()

    var inputBarBackgroundColor: Int
        get() = prefs.getInt(INPUT_BAR_BACKGROUND_COLOR, DEFAULT_DARK_GREY)
        set(inputBarBackgroundColor) = prefs.edit().putInt(INPUT_BAR_BACKGROUND_COLOR, inputBarBackgroundColor).apply()

    var inputBarTextColor: Int
        get() = prefs.getInt(INPUT_BAR_TEXT_COLOR, Color.WHITE)
        set(inputBarTextColor) = prefs.edit().putInt(INPUT_BAR_TEXT_COLOR, inputBarTextColor).apply()

    var sentBubbleColor: Int
        get() = prefs.getInt(SENT_BUBBLE_COLOR, DEFAULT_SENT_GREY)
        set(sentBubbleColor) = prefs.edit().putInt(SENT_BUBBLE_COLOR, sentBubbleColor).apply()

    var receivedBubbleColor: Int
        get() = prefs.getInt(RECEIVED_BUBBLE_COLOR, DEFAULT_LIGHT_GREY)
        set(receivedBubbleColor) = prefs.edit().putInt(RECEIVED_BUBBLE_COLOR, receivedBubbleColor).apply()

    var sentBubbleTextColor: Int
        get() = prefs.getInt(SENT_BUBBLE_TEXT_COLOR, Color.WHITE)
        set(sentBubbleTextColor) = prefs.edit().putInt(SENT_BUBBLE_TEXT_COLOR, sentBubbleTextColor).apply()

    var receivedBubbleTextColor: Int
        get() = prefs.getInt(RECEIVED_BUBBLE_TEXT_COLOR, Color.BLACK)
        set(receivedBubbleTextColor) = prefs.edit().putInt(RECEIVED_BUBBLE_TEXT_COLOR, receivedBubbleTextColor).apply()
}

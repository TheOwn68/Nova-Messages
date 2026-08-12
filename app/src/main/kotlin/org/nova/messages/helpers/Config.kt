package org.nova.messages.helpers

import android.content.Context
import android.graphics.Color
import org.fossify.commons.helpers.BaseConfig
import org.nova.messages.extensions.getDefaultKeyboardHeight
import org.nova.messages.models.Conversation

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
        
        // FINAL ABSOLUTE DEFAULTS
        val DEFAULT_DARK_GREY = Color.parseColor("#333333")
        val DEFAULT_LIGHT_GREY = Color.parseColor("#E0E0E0")
        val DEFAULT_SENT_GREY = Color.parseColor("#D8D8D8") // Even lighter grey, very close to receiver bubble
    }

    fun saveUseSIMIdAtNumber(number: String, SIMId: Int) {
        prefs.edit().putInt(USE_SIM_ID_PREFIX + number, SIMId).apply()
    }

    fun getUseSIMIdAtNumber(number: String) = prefs.getInt(USE_SIM_ID_PREFIX + number, 0)

    var showCharacterCounter: Boolean
        get() = prefs.getBoolean(SHOW_CHARACTER_COUNTER, false)
        set(showCharacterCounter) = prefs.edit()
            .putBoolean(SHOW_CHARACTER_COUNTER, showCharacterCounter).apply()

    var showDateTime: Boolean
        get() = prefs.getBoolean(SHOW_DATE_TIME, true)
        set(showDateTime) = prefs.edit()
            .putBoolean(SHOW_DATE_TIME, showDateTime).apply()

    var muteNonContactMessages: Boolean
        get() = prefs.getBoolean(MUTE_NON_CONTACT_MESSAGES, false)
        set(muteNonContactMessages) = prefs.edit()
            .putBoolean(MUTE_NON_CONTACT_MESSAGES, muteNonContactMessages).apply()

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
        get() = prefs.getBoolean(SEND_LONG_MESSAGE_MMS, true)
        set(sendLongMessageMMS) = prefs.edit().putBoolean(SEND_LONG_MESSAGE_MMS, sendLongMessageMMS)
            .apply()

    var sendGroupMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_GROUP_MESSAGE_MMS, true)
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

    var useNewUi: Boolean
        get() = prefs.getBoolean(USE_NEW_UI, true)
        set(useNewUi) = prefs.edit().putBoolean(USE_NEW_UI, useNewUi).apply()

    var conversationOrder: String
        get() = prefs.getString(CONVERSATION_ORDER, "")!!
        set(conversationOrder) = prefs.edit().putString(CONVERSATION_ORDER, conversationOrder).apply()

    var appLockType: Int
        get() = prefs.getInt(APP_LOCK_TYPE, LOCK_NONE)
        set(appLockType) = prefs.edit().putInt(APP_LOCK_TYPE, appLockType).apply()

    var appLockPassword: String
        get() = prefs.getString(APP_LOCK_PASSWORD, "")!!
        set(appLockPassword) = prefs.edit().putString(APP_LOCK_PASSWORD, appLockPassword).apply()

    var contactSortingMode: Int
        get() = prefs.getInt(CONTACT_SORTING_MODE, 0) // 0: Manual, 1: Alphabetical, 2: Recent
        set(contactSortingMode) = prefs.edit().putInt(CONTACT_SORTING_MODE, contactSortingMode).apply()

    var recentColor: Int
        get() = prefs.getInt(RECENT_COLOR, Color.parseColor("#FFE4E1"))
        set(recentColor) = prefs.edit().putInt(RECENT_COLOR, recentColor).apply()

    var row1Color: Int
        get() = prefs.getInt(ROW1_COLOR, Color.parseColor("#F48FB1"))
        set(row1Color) = prefs.edit().putInt(ROW1_COLOR, row1Color).apply()

    var row2Color: Int
        get() = prefs.getInt(ROW2_COLOR, Color.parseColor("#81C784"))
        set(row2Color) = prefs.edit().putInt(ROW2_COLOR, row2Color).apply()

    var row3Color: Int
        get() = prefs.getInt(ROW3_COLOR, Color.parseColor("#FFB74D"))
        set(row3Color) = prefs.edit().putInt(ROW3_COLOR, row3Color).apply()

    var uiScale: Float
        get() = prefs.getFloat(UI_SCALE, 1.0f)
        set(uiScale) = prefs.edit().putFloat(UI_SCALE, uiScale).apply()

    fun resetColors() {
        prefs.edit().remove(TOP_BAR_COLOR).remove(TOP_BAR_TEXT_COLOR).remove(MAIN_BACKGROUND_COLOR).remove(MAIN_TEXT_COLOR).remove(INPUT_BAR_BACKGROUND_COLOR).remove(INPUT_BAR_TEXT_COLOR).remove(SENT_BUBBLE_COLOR).remove(SENT_BUBBLE_TEXT_COLOR).remove(RECEIVED_BUBBLE_COLOR).remove(RECEIVED_BUBBLE_TEXT_COLOR).remove(RECENT_COLOR).remove(ROW1_COLOR).remove(ROW2_COLOR).remove(ROW3_COLOR)
            .remove(TOP_BAR_IMAGE).remove(MAIN_BACKGROUND_IMAGE).remove(INPUT_BAR_IMAGE)
            .remove(TOP_BAR_BG_MODE).remove(MAIN_BG_MODE).remove(INPUT_BAR_BG_MODE).apply()
    }

    var fontFamilyNova: Int
        get() = prefs.getInt(FONT_FAMILY_NOVA, 0) // Default to System
        set(fontFamilyNova) = prefs.edit().putInt(FONT_FAMILY_NOVA, fontFamilyNova).apply()

    var topBarColor: Int
        get() = prefs.getInt(TOP_BAR_COLOR, 0)
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
        get() = prefs.getInt(SENT_BUBBLE_TEXT_COLOR, Color.BLACK)
        set(sentBubbleTextColor) = prefs.edit().putInt(SENT_BUBBLE_TEXT_COLOR, sentBubbleTextColor).apply()

    var receivedBubbleTextColor: Int
        get() = prefs.getInt(RECEIVED_BUBBLE_TEXT_COLOR, Color.BLACK)
        set(receivedBubbleTextColor) = prefs.edit().putInt(RECEIVED_BUBBLE_TEXT_COLOR, receivedBubbleTextColor).apply()

    var topBarImage: String
        get() = prefs.getString(TOP_BAR_IMAGE, "")!!
        set(topBarImage) = prefs.edit().putString(TOP_BAR_IMAGE, topBarImage).apply()

    var mainBackgroundImage: String
        get() = prefs.getString(MAIN_BACKGROUND_IMAGE, "")!!
        set(mainBackgroundImage) = prefs.edit().putString(MAIN_BACKGROUND_IMAGE, mainBackgroundImage).apply()

    var inputBarImage: String
        get() = prefs.getString(INPUT_BAR_IMAGE, "")!!
        set(inputBarImage) = prefs.edit().putString(INPUT_BAR_IMAGE, inputBarImage).apply()

    var topBarBgMode: Int
        get() = prefs.getInt(TOP_BAR_BG_MODE, BG_MODE_COLOR)
        set(topBarBgMode) = prefs.edit().putInt(TOP_BAR_BG_MODE, topBarBgMode).apply()

    var mainBgMode: Int
        get() = prefs.getInt(MAIN_BG_MODE, BG_MODE_COLOR)
        set(mainBgMode) = prefs.edit().putInt(MAIN_BG_MODE, mainBgMode).apply()

    var inputBarBgMode: Int
        get() = prefs.getInt(INPUT_BAR_BG_MODE, BG_MODE_COLOR)
        set(inputBarBgMode) = prefs.edit().putInt(INPUT_BAR_BG_MODE, inputBarBgMode).apply()

    var topBarCropRect: String
        get() = prefs.getString(TOP_BAR_CROP_RECT, "")!!
        set(topBarCropRect) = prefs.edit().putString(TOP_BAR_CROP_RECT, topBarCropRect).apply()

    var mainBgCropRect: String
        get() = prefs.getString(MAIN_BG_CROP_RECT, "")!!
        set(mainBgCropRect) = prefs.edit().putString(MAIN_BG_CROP_RECT, mainBgCropRect).apply()

    var inputBarCropRect: String
        get() = prefs.getString(INPUT_BAR_CROP_RECT, "")!!
        set(inputBarCropRect) = prefs.edit().putString(INPUT_BAR_CROP_RECT, inputBarCropRect).apply()

    var notificationTextColor: Int
        get() = prefs.getInt(NOTIFICATION_TEXT_COLOR, Color.BLACK)
        set(notificationTextColor) = prefs.edit().putInt(NOTIFICATION_TEXT_COLOR, notificationTextColor).apply()

    var notificationTextOvalColor: Int
        get() = prefs.getInt(NOTIFICATION_TEXT_OVAL_COLOR, Color.WHITE)
        set(notificationTextOvalColor) = prefs.edit().putInt(NOTIFICATION_TEXT_OVAL_COLOR, notificationTextOvalColor).apply()

    var preset1Name: String
        get() = prefs.getString(PRESET_1_NAME, "Preset 1")!!
        set(preset1Name) = prefs.edit().putString(PRESET_1_NAME, preset1Name).apply()

    var preset2Name: String
        get() = prefs.getString(PRESET_2_NAME, "Preset 2")!!
        set(preset2Name) = prefs.edit().putString(PRESET_2_NAME, preset2Name).apply()

    var preset3Name: String
        get() = prefs.getString(PRESET_3_NAME, "Preset 3")!!
        set(preset3Name) = prefs.edit().putString(PRESET_3_NAME, preset3Name).apply()

    var topBarOutline: Boolean
        get() = prefs.getBoolean(TOP_BAR_OUTLINE, false)
        set(topBarOutline) = prefs.edit().putBoolean(TOP_BAR_OUTLINE, topBarOutline).apply()

    var topBarOutlineColor: Int
        get() = prefs.getInt(TOP_BAR_OUTLINE_COLOR, Color.BLACK)
        set(topBarOutlineColor) = prefs.edit().putInt(TOP_BAR_OUTLINE_COLOR, topBarOutlineColor).apply()

    var topBarOutlineThickness: Int
        get() = prefs.getInt(TOP_BAR_OUTLINE_THICKNESS, 2)
        set(topBarOutlineThickness) = prefs.edit().putInt(TOP_BAR_OUTLINE_THICKNESS, topBarOutlineThickness).apply()

    var searchBarOutline: Boolean
        get() = prefs.getBoolean(SEARCH_BAR_OUTLINE, false)
        set(searchBarOutline) = prefs.edit().putBoolean(SEARCH_BAR_OUTLINE, searchBarOutline).apply()

    var searchBarOutlineColor: Int
        get() = prefs.getInt(SEARCH_BAR_OUTLINE_COLOR, Color.BLACK)
        set(searchBarOutlineColor) = prefs.edit().putInt(SEARCH_BAR_OUTLINE_COLOR, searchBarOutlineColor).apply()

    var searchBarOutlineThickness: Int
        get() = prefs.getInt(SEARCH_BAR_OUTLINE_THICKNESS, 2)
        set(searchBarOutlineThickness) = prefs.edit().putInt(SEARCH_BAR_OUTLINE_THICKNESS, searchBarOutlineThickness).apply()

    var bigContactsOutline: Boolean
        get() = prefs.getBoolean(BIG_CONTACTS_OUTLINE, false)
        set(bigContactsOutline) = prefs.edit().putBoolean(BIG_CONTACTS_OUTLINE, bigContactsOutline).apply()

    var bigContactsOutlineColor: Int
        get() = prefs.getInt(BIG_CONTACTS_OUTLINE_COLOR, Color.BLACK)
        set(bigContactsOutlineColor) = prefs.edit().putInt(BIG_CONTACTS_OUTLINE_COLOR, bigContactsOutlineColor).apply()

    var bigContactsOutlineThickness: Int
        get() = prefs.getInt(BIG_CONTACTS_OUTLINE_THICKNESS, 2)
        set(bigContactsOutlineThickness) = prefs.edit().putInt(BIG_CONTACTS_OUTLINE_THICKNESS, bigContactsOutlineThickness).apply()

    var smallContactsOutline: Boolean
        get() = prefs.getBoolean(SMALL_CONTACTS_OUTLINE, false)
        set(smallContactsOutline) = prefs.edit().putBoolean(SMALL_CONTACTS_OUTLINE, smallContactsOutline).apply()

    var smallContactsOutlineColor: Int
        get() = prefs.getInt(SMALL_CONTACTS_OUTLINE_COLOR, Color.BLACK)
        set(smallContactsOutlineColor) = prefs.edit().putInt(SMALL_CONTACTS_OUTLINE_COLOR, smallContactsOutlineColor).apply()

    var smallContactsOutlineThickness: Int
        get() = prefs.getInt(SMALL_CONTACTS_OUTLINE_THICKNESS, 2)
        set(smallContactsOutlineThickness) = prefs.edit().putInt(SMALL_CONTACTS_OUTLINE_THICKNESS, smallContactsOutlineThickness).apply()

    var sentBubblesOutline: Boolean
        get() = prefs.getBoolean(SENT_BUBBLES_OUTLINE, false)
        set(sentBubblesOutline) = prefs.edit().putBoolean(SENT_BUBBLES_OUTLINE, sentBubblesOutline).apply()

    var sentBubblesOutlineColor: Int
        get() = prefs.getInt(SENT_BUBBLES_OUTLINE_COLOR, Color.BLACK)
        set(sentBubblesOutlineColor) = prefs.edit().putInt(SENT_BUBBLES_OUTLINE_COLOR, sentBubblesOutlineColor).apply()

    var sentBubblesOutlineThickness: Int
        get() = prefs.getInt(SENT_BUBBLES_OUTLINE_THICKNESS, 2)
        set(sentBubblesOutlineThickness) = prefs.edit().putInt(SENT_BUBBLES_OUTLINE_THICKNESS, sentBubblesOutlineThickness).apply()

    var receivedBubblesOutline: Boolean
        get() = prefs.getBoolean(RECEIVED_BUBBLES_OUTLINE, false)
        set(receivedBubblesOutline) = prefs.edit().putBoolean(RECEIVED_BUBBLES_OUTLINE, receivedBubblesOutline).apply()

    var receivedBubblesOutlineColor: Int
        get() = prefs.getInt(RECEIVED_BUBBLES_OUTLINE_COLOR, Color.BLACK)
        set(receivedBubblesOutlineColor) = prefs.edit().putInt(RECEIVED_BUBBLES_OUTLINE_COLOR, receivedBubblesOutlineColor).apply()

    var receivedBubblesOutlineThickness: Int
        get() = prefs.getInt(RECEIVED_BUBBLES_OUTLINE_THICKNESS, 2)
        set(receivedBubblesOutlineThickness) = prefs.edit().putInt(RECEIVED_BUBBLES_OUTLINE_THICKNESS, receivedBubblesOutlineThickness).apply()

    fun savePreset(id: Int) {
        val prefix = "preset_${id}_"
        val editor = prefs.edit()
        val themeKeys = listOf(
            TOP_BAR_COLOR, TOP_BAR_TEXT_COLOR, MAIN_TEXT_COLOR, MAIN_BACKGROUND_COLOR,
            INPUT_BAR_BACKGROUND_COLOR, INPUT_BAR_TEXT_COLOR, SENT_BUBBLE_COLOR,
            RECEIVED_BUBBLE_COLOR, SENT_BUBBLE_TEXT_COLOR, RECEIVED_BUBBLE_TEXT_COLOR,
            TOP_BAR_IMAGE, MAIN_BACKGROUND_IMAGE, INPUT_BAR_IMAGE,
            TOP_BAR_CROP_RECT, MAIN_BG_CROP_RECT, INPUT_BAR_CROP_RECT,
            TOP_BAR_BG_MODE, MAIN_BG_MODE, INPUT_BAR_BG_MODE,
            RECENT_COLOR, ROW1_COLOR, ROW2_COLOR, ROW3_COLOR,
            FONT_FAMILY_NOVA, UI_SCALE, USE_NEW_UI,
            TOP_BAR_OUTLINE, TOP_BAR_OUTLINE_COLOR, SEARCH_BAR_OUTLINE, SEARCH_BAR_OUTLINE_COLOR,
            BIG_CONTACTS_OUTLINE, BIG_CONTACTS_OUTLINE_COLOR, SMALL_CONTACTS_OUTLINE, SMALL_CONTACTS_OUTLINE_COLOR,
            SENT_BUBBLES_OUTLINE, SENT_BUBBLES_OUTLINE_COLOR, RECEIVED_BUBBLES_OUTLINE, RECEIVED_BUBBLES_OUTLINE_COLOR,
            PRESET_1_NAME, PRESET_2_NAME, PRESET_3_NAME // Include names in preset
        )
        themeKeys.forEach { key ->
            val value = prefs.all[key]
            if (value != null) {
                when (value) {
                    is Int -> editor.putInt(prefix + key, value)
                    is String -> editor.putString(prefix + key, value)
                    is Boolean -> editor.putBoolean(prefix + key, value)
                    is Float -> editor.putFloat(prefix + key, value)
                    is Long -> editor.putLong(prefix + key, value)
                }
            }
        }
        editor.apply()
    }

    fun loadPreset(id: Int) {
        val prefix = "preset_${id}_"
        val editor = prefs.edit()
        val themeKeys = listOf(
            TOP_BAR_COLOR, TOP_BAR_TEXT_COLOR, MAIN_TEXT_COLOR, MAIN_BACKGROUND_COLOR,
            INPUT_BAR_BACKGROUND_COLOR, INPUT_BAR_TEXT_COLOR, SENT_BUBBLE_COLOR,
            RECEIVED_BUBBLE_COLOR, SENT_BUBBLE_TEXT_COLOR, RECEIVED_BUBBLE_TEXT_COLOR,
            TOP_BAR_IMAGE, MAIN_BACKGROUND_IMAGE, INPUT_BAR_IMAGE,
            TOP_BAR_CROP_RECT, MAIN_BG_CROP_RECT, INPUT_BAR_CROP_RECT,
            TOP_BAR_BG_MODE, MAIN_BG_MODE, INPUT_BAR_BG_MODE,
            RECENT_COLOR, ROW1_COLOR, ROW2_COLOR, ROW3_COLOR,
            FONT_FAMILY_NOVA, UI_SCALE, USE_NEW_UI,
            TOP_BAR_OUTLINE, TOP_BAR_OUTLINE_COLOR, SEARCH_BAR_OUTLINE, SEARCH_BAR_OUTLINE_COLOR,
            BIG_CONTACTS_OUTLINE, BIG_CONTACTS_OUTLINE_COLOR, SMALL_CONTACTS_OUTLINE, SMALL_CONTACTS_OUTLINE_COLOR,
            SENT_BUBBLES_OUTLINE, SENT_BUBBLES_OUTLINE_COLOR, RECEIVED_BUBBLES_OUTLINE, RECEIVED_BUBBLES_OUTLINE_COLOR,
            PRESET_1_NAME, PRESET_2_NAME, PRESET_3_NAME
        )
        themeKeys.forEach { key ->
            val value = prefs.all[prefix + key]
            if (value != null) {
                when (value) {
                    is Int -> editor.putInt(key, value)
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Long -> editor.putLong(key, value)
                }
            }
        }
        editor.apply()
    }
}

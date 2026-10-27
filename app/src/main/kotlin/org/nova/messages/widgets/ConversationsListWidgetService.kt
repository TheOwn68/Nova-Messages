package org.nova.messages.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.fossify.commons.helpers.SimpleContactsHelper
import org.nova.messages.R
import org.nova.messages.extensions.conversationsDB
import org.nova.messages.extensions.stripCountryCodePrefix
import org.nova.messages.models.Conversation

class ConversationsListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return ConversationsListFactory(applicationContext, widgetId)
    }
}

class ConversationsListFactory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {
    private var conversations: List<Conversation> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        conversations = try {
            context.conversationsDB.getNonArchived().take(20)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun onDestroy() {
        conversations = emptyList()
    }

    override fun getCount(): Int = conversations.size

    override fun getViewAt(position: Int): RemoteViews {
        val conv = conversations.getOrElse(position) { return RemoteViews(context.applicationContext.packageName, R.layout.widget_conversations_item) }
        val rv = RemoteViews(context.applicationContext.packageName, R.layout.widget_conversations_item)

        val titleText = conv.title.ifEmpty { conv.phoneNumber }.stripCountryCodePrefix()
        rv.setTextViewText(R.id.widget_item_title, titleText)
        rv.setTextViewText(R.id.widget_item_snippet, conv.snippet)

        val letterIcon = SimpleContactsHelper(context).getContactLetterIcon(titleText)
        if (letterIcon != null) {
            rv.setImageViewBitmap(R.id.widget_item_avatar, letterIcon)
        }

        val prefs = context.getSharedPreferences("widget_prefs_$widgetId", Context.MODE_PRIVATE)
        val textColor = prefs.getInt("text_color", Color.WHITE)
        rv.setTextColor(R.id.widget_item_title, textColor)
        rv.setTextColor(R.id.widget_item_snippet, textColor)

        if (conv.unreadCount > 0) {
            rv.setViewVisibility(R.id.widget_item_unread, android.view.View.VISIBLE)
            rv.setTextViewText(R.id.widget_item_unread, conv.unreadCount.toString())
        } else {
            rv.setViewVisibility(R.id.widget_item_unread, android.view.View.GONE)
        }

        // Click whole card to open thread
        val fillInIntent = Intent().apply {
            putExtra("thread_id", conv.threadId)
        }
        rv.setOnClickFillInIntent(R.id.widget_item_holder, fillInIntent)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = conversations.getOrElse(position) { null }?.threadId ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

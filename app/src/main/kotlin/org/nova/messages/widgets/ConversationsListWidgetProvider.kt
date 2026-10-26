package org.nova.messages.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import org.nova.messages.R
import org.nova.messages.activities.MainActivity
import org.nova.messages.activities.ThreadActivity

class ConversationsListWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context.applicationContext, ConversationsListWidgetProvider::class.java))
            onUpdate(context, appWidgetManager, ids)
        }
    }

    companion object {
        const val ACTION_REFRESH = "org.nova.messages.widget.REFRESH"
        const val EXTRA_THREAD_ID = "thread_id"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("widget_prefs_$appWidgetId", Context.MODE_PRIVATE)
            val bgColor = prefs.getInt("bg_color", Color.parseColor("#CC1A1A1A"))
            val textColor = prefs.getInt("text_color", Color.WHITE)

            val views = RemoteViews(context.applicationContext.packageName, R.layout.widget_conversations_list)

            // Apply colors
            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)
            views.setTextColor(R.id.widget_title, textColor)
            views.setTextColor(R.id.widget_empty, textColor)

            // Title click → open app
            val openAppIntent = Intent().setClassName(context.applicationContext.packageName, MainActivity::class.java.name).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, openAppPending)

            // List view with remote adapter
            val serviceIntent = Intent().setClassName(context.applicationContext.packageName, ConversationsListWidgetService::class.java.name).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)

            // Click handler for list items
            val clickIntent = Intent().setClassName(context.applicationContext.packageName, ConversationsListWidgetProvider::class.java.name).apply {
                action = "org.nova.messages.widget.OPEN_THREAD"
            }
            val clickPending = PendingIntent.getBroadcast(
                context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, clickPending)

            // Empty view
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
        }
    }
}

// BroadcastReceiver for open thread clicks
class WidgetOpenThreadReceiver : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "org.nova.messages.widget.OPEN_THREAD") {
            val threadId = intent.getLongExtra("thread_id", -1L)
            if (threadId != -1L) {
                val openIntent = Intent().setClassName(context.applicationContext.packageName, ThreadActivity::class.java.name).apply {
                    putExtra("thread_id", threadId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(openIntent)
            }
        }
    }
}
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
import org.nova.messages.helpers.THREAD_ID

class ConversationsListWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val appContext = context.applicationContext
        for (appWidgetId in appWidgetIds) {
            updateWidget(appContext, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appContext = context.applicationContext
        if (intent.action == ACTION_OPEN_THREAD) {
            val threadId = intent.getLongExtra("thread_id", -1L)
            if (threadId != -1L) {
                val openIntent = Intent(appContext, ThreadActivity::class.java).apply {
                    putExtra(THREAD_ID, threadId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                appContext.startActivity(openIntent)
            }
        } else if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(appContext)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(appContext, ConversationsListWidgetProvider::class.java))
            onUpdate(appContext, appWidgetManager, ids)
        }
    }

    companion object {
        const val ACTION_REFRESH = "org.nova.messages.widget.REFRESH"
        const val ACTION_OPEN_THREAD = "org.nova.messages.widget.OPEN_THREAD"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences("widget_prefs_$appWidgetId", Context.MODE_PRIVATE)
            val bgColor = prefs.getInt("bg_color", Color.parseColor("#CC1A1A1A"))
            val textColor = prefs.getInt("text_color", Color.WHITE)

            val views = RemoteViews(appContext.packageName, R.layout.widget_conversations_list)

            // Apply colors
            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)
            views.setTextColor(R.id.widget_title, textColor)
            views.setTextColor(R.id.widget_empty, textColor)

            // Title click → open app
            val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                appContext, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, openAppPending)

            // List view with remote adapter
            val serviceIntent = Intent(appContext, ConversationsListWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)

            // Click handler for list items
            val clickIntent = Intent(appContext, ConversationsListWidgetProvider::class.java).apply {
                action = ACTION_OPEN_THREAD
            }
            val clickPending = PendingIntent.getBroadcast(
                appContext, 0, clickIntent,
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

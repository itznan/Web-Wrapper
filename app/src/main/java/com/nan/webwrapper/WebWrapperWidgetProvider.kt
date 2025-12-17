package com.nan.webwrapper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import java.net.URI

class WebWrapperWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_webwrapper)

            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPending = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                pendingIntentFlags()
            )
            views.setOnClickPendingIntent(R.id.widgetOpenAppButton, openAppPending)

            val history = HistoryRepository(context).getHistory().take(4)
            val buttonIds = listOf(
                R.id.widgetSite1Button,
                R.id.widgetSite2Button,
                R.id.widgetSite3Button,
                R.id.widgetSite4Button
            )

            buttonIds.forEachIndexed { index, viewId ->
                val entry = history.getOrNull(index)
                if (entry?.url.isNullOrBlank()) {
                    views.setViewVisibility(viewId, android.view.View.GONE)
                } else {
                    val label = entry!!.customName?.takeIf { it.isNotBlank() } ?: extractHost(entry.url) ?: entry.url
                    views.setTextViewText(viewId, label)
                    views.setViewVisibility(viewId, android.view.View.VISIBLE)

                    val openSiteIntent = Intent(context, WebViewActivity::class.java).apply {
                        putExtra(WebViewActivity.EXTRA_URL, entry.url)
                    }
                    val pending = PendingIntent.getActivity(
                        context,
                        100 + index,
                        openSiteIntent,
                        pendingIntentFlags()
                    )
                    views.setOnClickPendingIntent(viewId, pending)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun extractHost(url: String): String? {
            return try {
                val uri = URI(url)
                uri.host?.removePrefix("www.")
            } catch (_: Exception) {
                null
            }
        }

        private fun pendingIntentFlags(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        }
    }
}

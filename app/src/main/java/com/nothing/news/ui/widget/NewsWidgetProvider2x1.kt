package com.nothing.news.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nothing.news.MainActivity
import com.nothing.news.R
import com.nothing.news.data.local.dao.NewsDao
import com.nothing.news.worker.NewsWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NewsWidgetProvider2x1 : AppWidgetProvider() {

    @Inject
    lateinit var newsDao: NewsDao

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        const val ACTION_SYNC = "com.nothing.news.ACTION_SYNC_WIDGET"
        private const val PREFS_NAME = "news_widget_prefs"
        private const val KEY_IS_SYNCING = "is_syncing_2x1"

        fun triggerUpdate(context: Context) {
            val intent = Intent(context, NewsWidgetProvider2x1::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, NewsWidgetProvider2x1::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }

        fun isSyncing(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_IS_SYNCING, false)
        }

        fun setSyncing(context: Context, syncing: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_SYNCING, syncing)
                .apply()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_SYNC) {
            setSyncing(context, true)
            triggerUpdate(context)
            
            val syncRequest = OneTimeWorkRequestBuilder<NewsWorker>().build()
            WorkManager.getInstance(context).enqueue(syncRequest)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
            val unreadCount = newsDao.getUnreadCount()
            val isSyncing = isSyncing(context)
            
            val views = RemoteViews(context.packageName, R.layout.news_widget_2x1)
            
            val bitmap = createWidgetBitmap(context, unreadCount, isSyncing)
            views.setImageViewBitmap(R.id.widget_image, bitmap)

            // Dynamic Background: Red Pill when syncing, Default Pill otherwise
            val bgRes = if (isSyncing) R.drawable.widget_background_pill_red else R.drawable.widget_background_pill
            views.setInt(R.id.widget_container, "setBackgroundResource", bgRes)

            // Show/Hide real animated ProgressBar
            views.setViewVisibility(R.id.widget_progress, if (isSyncing) android.view.View.VISIBLE else android.view.View.GONE)

            if (unreadCount > 0) {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
                views.setOnClickPendingIntent(R.id.widget_image, pendingIntent)
            } else {
                if (isSyncing) {
                    views.setOnClickPendingIntent(R.id.widget_container, null)
                    views.setOnClickPendingIntent(R.id.widget_image, null)
                } else {
                    val intent = Intent(context, NewsWidgetProvider2x1::class.java).apply {
                        action = ACTION_SYNC
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context, 200, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_image, pendingIntent)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createWidgetBitmap(context: Context, unreadCount: Int, isSyncing: Boolean): Bitmap {
        val width = 600
        val height = 300
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Transparent background for the bitmap (handled by widget_container)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val ndot57 = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.ndot57)
        val ntype82 = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.ntype82)

        // Paint for text: Always White
        val newsPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            textSize = 110f
            typeface = ndot57
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        
        val newsX = 40f
        val centerY = height / 2f + 35f
        canvas.drawText("NEWS", newsX, centerY, newsPaint)

        if (unreadCount > 0) {
            val countPaint = Paint().apply {
                color = Color.parseColor("#FF2D00")
                style = Paint.Style.FILL
                textSize = 120f
                typeface = ntype82
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            val margin = 80f
            canvas.drawText(unreadCount.toString(), width - margin, centerY, countPaint)
        } else if (!isSyncing) {
            val xPaint = Paint().apply {
                color = Color.parseColor("#A0A0A0") // Lighter gray for better contrast
                style = Paint.Style.STROKE
                strokeWidth = 14f
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }
            
            val iconSize = 40f
            val iconX = width - 110f
            val iconY = height / 2f
            
            canvas.drawLine(iconX - iconSize, iconY - iconSize, iconX + iconSize, iconY + iconSize, xPaint)
            canvas.drawLine(iconX + iconSize, iconY - iconSize, iconX - iconSize, iconY + iconSize, xPaint)
        }

        return bitmap
    }
}

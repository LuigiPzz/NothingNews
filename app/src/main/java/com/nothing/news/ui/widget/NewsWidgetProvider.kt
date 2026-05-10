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
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.RemoteViews
import com.nothing.news.MainActivity
import com.nothing.news.R
import com.nothing.news.data.local.dao.NewsDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NewsWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var newsDao: NewsDao

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        fun triggerUpdate(context: Context) {
            val intent = Intent(context, NewsWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, NewsWidgetProvider::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
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
            
            val views = RemoteViews(context.packageName, R.layout.news_widget)
            
            val bitmap = createWidgetBitmap(context, unreadCount)
            views.setImageViewBitmap(R.id.widget_image, bitmap)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createWidgetBitmap(context: Context, unreadCount: Int): Bitmap {
        val width = 300
        val height = 300
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Load authentic Nothing fonts
        val ndot57 = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.ndot57)
        val ntype82 = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.ntype82)

        // Draw "NEWS" using NDot-57
        val newsPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            textSize = 100f // Set to 100f as requested
            typeface = ndot57
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        // Positioned with more top padding
        val newsY = height / 2.8f
        canvas.drawText("NEWS", width / 2f, newsY, newsPaint)

        // Draw the unread count using NType-82
        val countPaint = Paint().apply {
            color = Color.parseColor("#FF2D00") // Nothing Red
            style = Paint.Style.FILL
            textSize = 120f
            typeface = ntype82
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        
        // Positioned bottom-right (decreased margin for rectangular shape)
        val margin = 40f
        canvas.drawText(unreadCount.toString(), width - margin, height - margin, countPaint)

        return bitmap
    }
}

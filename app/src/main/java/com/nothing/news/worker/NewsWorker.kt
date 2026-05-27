package com.nothing.news.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nothing.news.data.repository.NewsRepository
import com.nothing.news.ui.widget.NewsWidgetProvider
import com.nothing.news.ui.widget.NewsWidgetProvider2x1
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class NewsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: NewsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            kotlinx.coroutines.withTimeout(10 * 60 * 1000L) { // 10 minutes timeout
                val feeds = repository.allFeeds.first()
                for (feed in feeds) {
                    repository.fetchNewsForSource(feed)
                }
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            NewsWidgetProvider.setSyncing(applicationContext, false)
            NewsWidgetProvider2x1.setSyncing(applicationContext, false)
            NewsWidgetProvider.triggerUpdate(applicationContext)
            NewsWidgetProvider2x1.triggerUpdate(applicationContext)
        }
    }
}

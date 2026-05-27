package com.nothing.news.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.nothing.news.data.local.PreferenceManager
import com.nothing.news.worker.NewsWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                val hours = preferenceManager.backgroundUpdateFrequency.first()
                if (hours > 0) {
                    val workManager = WorkManager.getInstance(context)
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                    val workRequest = PeriodicWorkRequestBuilder<NewsWorker>(
                        hours.toLong(), TimeUnit.HOURS
                    )
                    .setConstraints(constraints)
                    .addTag("news_update")
                    .build()
                    
                    workManager.enqueueUniquePeriodicWork(
                        "news_update",
                        ExistingPeriodicWorkPolicy.KEEP, // Keep existing if any, otherwise schedule
                        workRequest
                    )
                }
            }
        }
    }
}

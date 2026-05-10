package com.nothing.news.util

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.*

object NewsDateUtils {
    private val legacyFormats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss z",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd HH:mm:ss",
        "EEE, dd MMM yyyy HH:mm:ss",
        "dd MMM yyyy HH:mm:ss"
    )

    fun parseToTimestamp(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L
        val cleaned = dateString.trim()

        for (fmt in legacyFormats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                val date = sdf.parse(cleaned)
                if (date != null) return date.time
            } catch (e: Exception) { }
        }
        
        return 0L
    }

    fun formatRelativeTime(timestamp: Long, fallback: String?): String {
        if (timestamp <= 0L) return fallback ?: ""
        val now = System.currentTimeMillis()
        return try {
            DateUtils.getRelativeTimeSpanString(
                timestamp,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        } catch (e: Exception) {
            fallback ?: ""
        }
    }
}

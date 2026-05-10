package com.nothing.news.util

import com.google.gson.annotations.SerializedName

data class BackupData(
    @SerializedName("feed_urls")
    val feedUrls: List<String>,
    @SerializedName("favorite_links")
    val favoriteLinks: List<String>,
    @SerializedName("version")
    val version: Int = 1,
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

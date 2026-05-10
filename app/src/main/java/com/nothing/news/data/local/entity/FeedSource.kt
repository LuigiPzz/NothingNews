package com.nothing.news.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feed_sources")
data class FeedSource(
    @PrimaryKey val url: String,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

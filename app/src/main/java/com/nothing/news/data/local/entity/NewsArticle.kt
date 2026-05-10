package com.nothing.news.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_articles")
data class NewsArticle(
    @PrimaryKey val link: String,
    val title: String,
    val author: String?,
    val description: String?,
    val content: String?,
    val pubDate: String?,
    val pubDateTimestamp: Long = 0L,
    val imageUrl: String?,
    val sourceName: String,
    val feedUrl: String,
    val isRead: Boolean = false,
    val isFavorite: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis()
)

package com.nothing.news.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nothing.news.data.local.dao.NewsDao
import com.nothing.news.data.local.entity.FeedSource
import com.nothing.news.data.local.entity.NewsArticle

@Database(entities = [NewsArticle::class, FeedSource::class], version = 3, exportSchema = false)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun newsDao(): NewsDao
}

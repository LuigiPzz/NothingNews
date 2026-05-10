package com.nothing.news.data.local.dao

import androidx.room.*
import com.nothing.news.data.local.entity.FeedSource
import com.nothing.news.data.local.entity.NewsArticle
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {
    @Query("SELECT * FROM news_articles ORDER BY pubDateTimestamp DESC")
    fun getAllNews(): Flow<List<NewsArticle>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNews(articles: List<NewsArticle>)

    @Query("UPDATE news_articles SET imageUrl = :imageUrl WHERE link = :link AND (imageUrl IS NULL OR imageUrl = '')")
    suspend fun updateImageIfMissing(link: String, imageUrl: String?)

    @Query("UPDATE news_articles SET isRead = :isRead WHERE link = :link")
    suspend fun updateReadStatus(link: String, isRead: Boolean)

    @Query("UPDATE news_articles SET isRead = 1 WHERE isRead = 0 AND fetchedAt < :timestamp")
    suspend fun markOldAsRead(timestamp: Long)

    @Query("UPDATE news_articles SET isRead = 0")
    suspend fun resetAllReadStatus()

    @Query("SELECT COUNT(*) FROM news_articles WHERE isRead = 0")
    suspend fun getUnreadCount(): Int

    @Query("UPDATE news_articles SET isFavorite = :isFavorite WHERE link = :link")
    suspend fun updateFavoriteStatus(link: String, isFavorite: Boolean)

    @Query("DELETE FROM news_articles WHERE isFavorite = 0 AND pubDateTimestamp < :timestamp AND fetchedAt < :timestamp")
    suspend fun deleteOldNews(timestamp: Long)

    @Query("DELETE FROM news_articles WHERE link = :link")
    suspend fun deleteArticle(link: String)

    // Feed Source management
    @Query("SELECT * FROM feed_sources")
    fun getAllFeedSources(): Flow<List<FeedSource>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedSource(feedSource: FeedSource)

    @Query("DELETE FROM feed_sources WHERE url = :url")
    suspend fun deleteFeedSource(url: String)

    @Query("DELETE FROM news_articles WHERE feedUrl = :feedUrl")
    suspend fun deleteNewsByFeed(feedUrl: String)

    @Query("DELETE FROM feed_sources")
    suspend fun deleteAllFeedSources()

    @Query("DELETE FROM news_articles")
    suspend fun deleteAllNews()
}

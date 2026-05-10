package com.nothing.news.di

import android.content.Context
import androidx.room.Room
import com.nothing.news.data.local.NewsDatabase
import com.nothing.news.data.local.dao.NewsDao
import com.nothing.news.data.remote.FeedSearchService
import com.prof18.rssparser.RssParser
import com.prof18.rssparser.RssParserBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NewsDatabase {
        return Room.databaseBuilder(
            context,
            NewsDatabase::class.java,
            "nothing_news_v5.db"
        ).fallbackToDestructiveMigration()
         .build()
    }

    @Provides
    fun provideNewsDao(database: NewsDatabase): NewsDao {
        return database.newsDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "application/xml,application/rss+xml,text/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Cache-Control", "no-cache")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRssParser(client: OkHttpClient): RssParser {
        return RssParserBuilder(
            callFactory = client
        ).build()
    }

    @Provides
    @Singleton
    fun provideFeedSearchService(okHttpClient: OkHttpClient): FeedSearchService {
        return Retrofit.Builder()
            .baseUrl("https://feedsearch.dev/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FeedSearchService::class.java)
    }

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context, okHttpClient: OkHttpClient): coil.ImageLoader {
        return coil.ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .build()
    }
    @Provides
    @Singleton
    fun provideAiRepository(
        okHttpClient: OkHttpClient,
        preferenceManager: com.nothing.news.data.local.PreferenceManager
    ): com.nothing.news.data.repository.AiRepository {
        return com.nothing.news.data.repository.AiRepository(okHttpClient, preferenceManager)
    }
}

package com.nothing.news.data.repository

import com.nothing.news.data.local.dao.NewsDao
import com.nothing.news.data.local.entity.FeedSource
import com.nothing.news.data.local.entity.NewsArticle
import com.prof18.rssparser.RssParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.awaitClose
import com.nothing.news.data.remote.FeedSearchService
import com.nothing.news.data.remote.FeedsearchResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsRepository @Inject constructor(
    private val newsDao: NewsDao,
    private val rssParser: RssParser,
    private val feedSearchService: FeedSearchService,
    private val okHttpClient: okhttp3.OkHttpClient,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    val allNews: Flow<List<NewsArticle>> = newsDao.getAllNews()
    val allFeeds: Flow<List<FeedSource>> = newsDao.getAllFeedSources()

    fun searchFeeds(query: String): kotlinx.coroutines.flow.Flow<List<FeedsearchResult>> = kotlinx.coroutines.flow.channelFlow {
        val isDomain = query.contains(".")
        val results = java.util.concurrent.CopyOnWriteArrayList<FeedsearchResult>()
        
        fun updateAndSend(newResults: List<FeedsearchResult>) {
            val currentUrls = results.map { it.url.lowercase().trimEnd('/') }.toSet()
            val uniqueNew = newResults.filter { it.url.lowercase().trimEnd('/') !in currentUrls }
            
            if (uniqueNew.isNotEmpty()) {
                results.addAll(uniqueNew)
                trySend(results.toList().distinctBy { it.url.lowercase().trimEnd('/') }.sortedBy { it.title })
            }
        }

        // 1. Feedly Search
        launch {
            try {
                val feedlyResponse = feedSearchService.searchFeedsByName(query)
                updateAndSend(feedlyResponse.results.map {
                    FeedsearchResult(it.feedId.replace("feed/", ""), it.title, null, null, null, null)
                })
                feedlyResponse.results.mapNotNull { it.website }.distinct().take(3).forEach { domain ->
                    launch { try { updateAndSend(feedSearchService.searchFeedsDeep(domain)) } catch (e: Exception) {} }
                }
            } catch (e: Exception) {}
        }
        
        // 2. Direct Deep Search
        if (isDomain) {
            launch { try { updateAndSend(feedSearchService.searchFeedsDeep(query)) } catch (e: Exception) {} }
        }
        
        // 3. Manual Probing & HTML Discovery
        val candidateDomains = mutableListOf<String>()
        if (isDomain) candidateDomains.add(query)
        else if (query.length > 3) {
            listOf(".it", ".com", ".net", ".org", ".io", ".me").forEach { candidateDomains.add(query + it) }
        }

        candidateDomains.forEach { domain ->
            launch {
                val base = if (domain.startsWith("http")) domain else "https://$domain"
                
                // HTML Discovery
                launch {
                    try {
                        val request = okhttp3.Request.Builder().url(base).header("User-Agent", "Mozilla/5.0").build()
                        okHttpClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val html = response.body?.string() ?: ""
                                val rssRegex = """<link[^>]+type=["']application/(rss|atom)\+xml["'][^>]+href=["']([^"']+)["']""".toRegex()
                                rssRegex.findAll(html).forEach { match ->
                                    val feedUrl = match.groupValues[2]
                                    val absoluteUrl = if (feedUrl.startsWith("http")) feedUrl else {
                                        if (feedUrl.startsWith("/")) base.trimEnd('/') + feedUrl else "$base/$feedUrl"
                                    }
                                    launch {
                                        try {
                                            val channel = rssParser.getRssChannel(absoluteUrl)
                                            updateAndSend(listOf(FeedsearchResult(absoluteUrl, channel.title ?: domain, channel.description, domain, channel.image?.url, "RSS (Detected)")))
                                        } catch (e: Exception) {}
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }

                // Path Probing
                listOf("/feed", "/feed/", "/rss", "/rss/", "/feed.xml", "/rss.xml").forEach { path ->
                    val url = if (base.endsWith("/") && path.startsWith("/")) base + path.substring(1)
                    else if (!base.endsWith("/") && !path.startsWith("/")) "$base/$path"
                    else base + path
                    launch {
                        try {
                            kotlinx.coroutines.withTimeout(5000) {
                                val channel = rssParser.getRssChannel(url)
                                updateAndSend(listOf(FeedsearchResult(url, channel.title ?: domain, channel.description, domain, channel.image?.url, "RSS (Probe)")))
                            }
                        } catch (e: Exception) {}
                    }
                }
            }
        }
        awaitClose {}
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    suspend fun markOldAsRead(days: Int) {
        if (days <= 0) return
        val timestamp = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        newsDao.markOldAsRead(timestamp)
        com.nothing.news.ui.widget.NewsWidgetProvider.triggerUpdate(context)
    }

    suspend fun deleteOldNews(days: Int) {
        if (days <= 0) return
        val timestamp = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        newsDao.deleteOldNews(timestamp)
        com.nothing.news.ui.widget.NewsWidgetProvider.triggerUpdate(context)
    }

    suspend fun fetchNewsForSource(feedSource: FeedSource) {
        try {
            val xmlString = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val request = okhttp3.Request.Builder().url(feedSource.url).build()
                okHttpClient.newCall(request).execute().body?.string() ?: ""
            }

            if (xmlString.isBlank()) return

            val channel = rssParser.parse(xmlString)
            val currentFetchedAt = System.currentTimeMillis()
            val articles = channel.items.mapIndexed { index, item ->
                var imageUrl = item.image
                
                // Deep extraction for tags like media:content or media:thumbnail
                if (imageUrl.isNullOrBlank()) {
                    val itemTitle = item.title?.let { java.util.regex.Pattern.quote(it) } ?: ""
                    if (itemTitle.isNotBlank()) {
                        val mediaRegex = """<title>$itemTitle</title>.*?(?:<media:content|<media:thumbnail)[^>]+url=["']([^"']+)["']""".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        imageUrl = mediaRegex.find(xmlString)?.groupValues?.get(1)
                    }
                }
                
                if (imageUrl.isNullOrBlank()) {
                    val itemLink = item.link?.let { java.util.regex.Pattern.quote(it) } ?: ""
                    if (itemLink.isNotBlank()) {
                        val linkMediaRegex = """<link>$itemLink</link>.*?(?:<media:content|<media:thumbnail)[^>]+url=["']([^"']+)["']""".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        imageUrl = linkMediaRegex.find(xmlString)?.groupValues?.get(1)
                    }
                }
                
                if (imageUrl.isNullOrBlank()) {
                    val htmlContent = (item.description ?: "") + (item.content ?: "")
                    val imgRegex = """<img[^>]+src=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                    imageUrl = imgRegex.find(htmlContent)?.groupValues?.get(1)
                }

                val parsedTimestamp = com.nothing.news.util.NewsDateUtils.parseToTimestamp(item.pubDate)
                
                // Add a small offset (index) to ensure unique ordering even if timestamps are identical
                // We use subtraction because we want the first item in the RSS (usually newest) to have the largest timestamp
                val uniqueTimestamp = if (parsedTimestamp > 0) {
                    parsedTimestamp - index 
                } else {
                    currentFetchedAt - index
                }
                
                NewsArticle(
                    link = item.link ?: "",
                    title = item.title ?: "No Title",
                    author = item.author,
                    description = item.description,
                    content = item.content,
                    pubDate = item.pubDate,
                    pubDateTimestamp = uniqueTimestamp,
                    fetchedAt = currentFetchedAt,
                    imageUrl = imageUrl,
                    sourceName = feedSource.title,
                    feedUrl = feedSource.url
                )
            }.filter { it.link.isNotBlank() }
            
            newsDao.insertNews(articles)
            com.nothing.news.ui.widget.NewsWidgetProvider.triggerUpdate(context)
            
            // EXTRA FALLBACK: For articles still missing images, try to fetch from the web page (Increased to 15)
            val missingImages = articles.filter { it.imageUrl.isNullOrBlank() }.take(15)
            if (missingImages.isNotEmpty()) {
                kotlinx.coroutines.coroutineScope {
                    missingImages.forEach { article ->
                        launch(kotlinx.coroutines.Dispatchers.IO) {
                            val ogImage = fetchOgImageFromUrl(article.link)
                            if (!ogImage.isNullOrBlank()) {
                                newsDao.updateImageIfMissing(article.link, ogImage)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun fetchOgImageFromUrl(url: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()
            val response = okHttpClient.newCall(request).execute()
            val html = response.body?.string() ?: ""
            
            // More robust regex for og:image and twitter:image
            val ogRegex = """<meta[^>]+(?:property|name)=["'](?:og:image|twitter:image)["'][^>]+content=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
            var image = ogRegex.find(html)?.groupValues?.get(1)
            
            // Try another order of attributes
            if (image.isNullOrBlank()) {
                val ogRegexAlt = """<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["'](?:og:image|twitter:image)["']""".toRegex(RegexOption.IGNORE_CASE)
                image = ogRegexAlt.find(html)?.groupValues?.get(1)
            }
            
            image
        } catch (e: Exception) {
            null
        }
    }

    suspend fun addFeed(url: String) {
        val cleanedUrl = url.trim().replace(" ", "")
        
        try {
            // Try with original URL
            tryAddFeed(cleanedUrl)
        } catch (e: Exception) {
            // Fallback: if it was http, try https
            if (cleanedUrl.startsWith("http://")) {
                val httpsUrl = cleanedUrl.replace("http://", "https://")
                try {
                    tryAddFeed(httpsUrl)
                    return
                } catch (inner: Exception) {
                    throw e // throw original error if fallback also fails
                }
            } else if (cleanedUrl.startsWith("https://")) {
                // Or vice versa
                val httpUrl = cleanedUrl.replace("https://", "http://")
                try {
                    tryAddFeed(httpUrl)
                    return
                } catch (inner: Exception) {
                    throw e
                }
            } else if (!cleanedUrl.startsWith("http")) {
                // If no protocol, try both
                try {
                    tryAddFeed("https://$cleanedUrl")
                    return
                } catch (e1: Exception) {
                    try {
                        tryAddFeed("http://$cleanedUrl")
                        return
                    } catch (e2: Exception) {
                        throw e1
                    }
                }
            }
            throw e
        }
    }

    private suspend fun tryAddFeed(url: String) {
        val xmlString = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .build()
                    chain.proceed(request)
                }
                .build()
                
            val request = okhttp3.Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw Exception("Server error: ${response.code}")
            }
            
            val body = response.body?.string() ?: throw Exception("Empty response body")
            
            // Check if it's XML (RSS/Atom) or HTML
            if (body.trim().startsWith("<") && !body.trim().startsWith("<!DOCTYPE html", ignoreCase = true) && !body.trim().startsWith("<html", ignoreCase = true)) {
                body
            } else {
                // It might be HTML, try to discover feed URL
                val discoveredUrl = discoverFeedUrl(url, body)
                if (discoveredUrl != null && discoveredUrl != url) {
                    val secondRequest = okhttp3.Request.Builder().url(discoveredUrl).build()
                    val secondResponse = client.newCall(secondRequest).execute()
                    secondResponse.body?.string() ?: throw Exception("Discovered feed is empty")
                } else {
                    throw Exception("L'URL fornito non è un feed RSS valido.")
                }
            }
        }
        
        val channel = rssParser.parse(xmlString)
        val feedSource = FeedSource(
            url = url,
            title = channel.title ?: url,
            description = channel.description
        )
        newsDao.insertFeedSource(feedSource)
        fetchNewsForSource(feedSource)
    }

    private fun discoverFeedUrl(baseUrl: String, html: String): String? {
        // Simple regex to find <link rel="alternate" type="application/rss+xml" href="...">
        val rssRegex = """<link[^>]+type=["']application/(rss|atom)\+xml["'][^>]+href=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
        val match = rssRegex.find(html)
        return match?.groupValues?.get(2)?.let { discoveredPath ->
            if (discoveredPath.startsWith("http")) {
                discoveredPath
            } else {
                // Handle relative paths
                val base = baseUrl.trimEnd('/')
                if (discoveredPath.startsWith("/")) {
                    val domainOnly = base.split("/").take(3).joinToString("/")
                    domainOnly + discoveredPath
                } else {
                    "$base/$discoveredPath"
                }
            }
        }
    }

    suspend fun removeFeed(url: String) {
        newsDao.deleteNewsByFeed(url)
        newsDao.deleteFeedSource(url)
        com.nothing.news.ui.widget.NewsWidgetProvider.triggerUpdate(context)
    }

    suspend fun removeArticle(link: String) {
        newsDao.deleteArticle(link)
        com.nothing.news.ui.widget.NewsWidgetProvider.triggerUpdate(context)
    }

    suspend fun updateReadStatus(link: String, isRead: Boolean) {
        newsDao.updateReadStatus(link, isRead)
        com.nothing.news.ui.widget.NewsWidgetProvider.triggerUpdate(context)
    }

    suspend fun resetAllReadStatus() {
        newsDao.resetAllReadStatus()
        com.nothing.news.ui.widget.NewsWidgetProvider.triggerUpdate(context)
    }

    suspend fun toggleFavorite(link: String, isFavorite: Boolean) {
        newsDao.updateFavoriteStatus(link, isFavorite)
    }

    suspend fun getBackupData(): com.nothing.news.util.BackupData {
        val feeds = newsDao.getAllFeedSources().first()
        val feedUrls = feeds.map { it.url }
        
        // Only get links of articles that are actually favorited
        val favorites = newsDao.getAllNews().first().filter { it.isFavorite }.map { it.link }
        
        return com.nothing.news.util.BackupData(
            feedUrls = feedUrls,
            favoriteLinks = favorites
        )
    }

    suspend fun restoreBackupData(data: com.nothing.news.util.BackupData) {
        // 1. CLEAR CURRENT DATA (Overwrite/Substitution logic)
        newsDao.deleteAllFeedSources()
        newsDao.deleteAllNews()
        com.nothing.news.ui.widget.NewsWidgetProvider.triggerUpdate(context)
        
        // 2. Restore feeds
        data.feedUrls.forEach { url ->
            try {
                addFeed(url)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // 2. Restore favorite status
        // Note: This only works if the articles are already in the DB or will be fetched soon.
        // For a more robust solution, we'd need a separate persistent favorites table.
        data.favoriteLinks.forEach { link ->
            newsDao.updateFavoriteStatus(link, true)
        }
    }

    suspend fun saveExternalUrlAsFavorite(url: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            val html = response.body?.string() ?: ""

            // Estrazione titolo
            val titleRegex = """<title>(.*?)</title>""".toRegex(setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val rawTitle = titleRegex.find(html)?.groupValues?.get(1)?.trim() ?: url
            val title = androidx.core.text.HtmlCompat.fromHtml(rawTitle, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString()

            // Estrazione immagine (og:image)
            val ogImageRegex = """<meta[^>]*property=["']og:image["'][^>]*content=["']([^"']*)["']""".toRegex(RegexOption.IGNORE_CASE)
            val ogImageRegexAlt = """<meta[^>]*content=["']([^"']*)["'][^>]*property=["']og:image["']""".toRegex(RegexOption.IGNORE_CASE)
            var imageUrl = ogImageRegex.find(html)?.groupValues?.get(1) ?: ogImageRegexAlt.find(html)?.groupValues?.get(1)

            val currentFetchedAt = System.currentTimeMillis()

            val article = NewsArticle(
                link = url,
                title = title,
                author = null,
                description = "Link salvato dai preferiti",
                content = "",
                pubDate = null,
                pubDateTimestamp = currentFetchedAt,
                imageUrl = imageUrl,
                sourceName = "Condiviso",
                feedUrl = "shared",
                isRead = false,
                isFavorite = true,
                fetchedAt = currentFetchedAt
            )

            // Inserisce o aggiorna
            newsDao.insertNews(listOf(article))
        } catch (e: Exception) {
            e.printStackTrace()
            // In caso di errore di rete, salva solo l'URL
            val article = NewsArticle(
                link = url,
                title = url,
                author = null,
                description = "Link salvato (offline)",
                content = "",
                pubDate = null,
                pubDateTimestamp = System.currentTimeMillis(),
                imageUrl = null,
                sourceName = "Condiviso",
                feedUrl = "shared",
                isRead = false,
                isFavorite = true,
                fetchedAt = System.currentTimeMillis()
            )
            newsDao.insertNews(listOf(article))
        }
    }
}

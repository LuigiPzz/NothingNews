package com.nothing.news.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface FeedSearchService {
    // Feedsearch.dev for deep domain search
    @GET("https://feedsearch.dev/api/v1/search")
    suspend fun searchFeedsDeep(@Query("url") url: String): List<FeedsearchResult>

    // Feedly for keyword/name search
    @GET("https://cloud.feedly.com/v3/search/feeds")
    suspend fun searchFeedsByName(@Query("q") query: String): FeedlySearchResponse
}

data class FeedlySearchResponse(
    @SerializedName("results") val results: List<FeedlyResult>
)

data class FeedlyResult(
    @SerializedName("feedId") val feedId: String,
    @SerializedName("title") val title: String,
    @SerializedName("website") val website: String?
)

data class FeedsearchResult(
    @SerializedName("url") val url: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String?,
    @SerializedName("site_name") val siteName: String?,
    @SerializedName("favicon") val favicon: String?,
    @SerializedName("version") val version: String?
)

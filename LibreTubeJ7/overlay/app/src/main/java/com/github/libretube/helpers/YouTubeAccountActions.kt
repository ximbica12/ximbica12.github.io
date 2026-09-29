package com.github.libretube.helpers

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object YouTubeAccountActions {
    private const val API_BASE = "https://www.googleapis.com/youtube/v3"

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun rateVideo(accessToken: String, videoId: String, rating: String) {
        require(rating in setOf("like", "dislike", "none"))

        val url = "$API_BASE/videos/rate?id=$videoId&rating=$rating"
        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build())
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .build()

        http.newCall(request).execute().use { response ->
            if (response.isSuccessful) return

            val body = response.body?.string().orEmpty()
            throw IOException(
                body.ifBlank { "YouTube Data API: HTTP ${response.code}" }
            )
        }
    }
}

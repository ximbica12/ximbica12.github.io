package com.github.libretube.helpers

import android.net.Uri
import com.github.libretube.api.PlaylistsHelper
import com.github.libretube.api.SubscriptionHelper
import com.github.libretube.obj.PipedImportPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object YouTubeDirectImport {
    private const val API_BASE = "https://www.googleapis.com/youtube/v3"
    private const val MAX_RESULTS = "50"
    private val http = OkHttpClient()

    data class ImportResult(
        val subscriptions: Int,
        val playlists: Int,
        val playlistVideos: Int,
        val likedVideos: Int
    )

    private data class RemotePlaylist(
        val id: String,
        val title: String
    )

    suspend fun importAll(accessToken: String): ImportResult = withContext(Dispatchers.IO) {
        val subscriptions = fetchSubscriptions(accessToken)
        if (subscriptions.isNotEmpty()) {
            SubscriptionHelper.importSubscriptions(subscriptions)
        }

        val remotePlaylists = fetchOwnedPlaylists(accessToken)
        val importedPlaylists = mutableListOf<PipedImportPlaylist>()
        var playlistVideoCount = 0

        for (playlist in remotePlaylists) {
            val ids = fetchPlaylistItems(accessToken, playlist.id)
            playlistVideoCount += ids.size
            importedPlaylists += PipedImportPlaylist(
                name = playlist.title,
                type = "playlist",
                visibility = "private",
                videos = ids
            )
        }

        var likedCount = 0
        val likesPlaylistId = fetchLikesPlaylistId(accessToken)
        if (!likesPlaylistId.isNullOrBlank()) {
            val likes = fetchPlaylistItems(accessToken, likesPlaylistId)
            likedCount = likes.size
            if (likes.isNotEmpty()) {
                importedPlaylists += PipedImportPlaylist(
                    name = "Vídeos marcados como gostei (YouTube)",
                    type = "playlist",
                    visibility = "private",
                    videos = likes
                )
            }
        }

        if (importedPlaylists.isNotEmpty()) {
            PlaylistsHelper.importPlaylists(importedPlaylists)
        }

        ImportResult(
            subscriptions = subscriptions.size,
            playlists = remotePlaylists.size,
            playlistVideos = playlistVideoCount,
            likedVideos = likedCount
        )
    }

    private fun fetchSubscriptions(token: String): List<String> {
        val result = mutableListOf<String>()
        var pageToken: String? = null

        do {
            val json = get(
                token,
                "subscriptions",
                mapOf(
                    "part" to "snippet",
                    "mine" to "true",
                    "maxResults" to MAX_RESULTS,
                    "pageToken" to pageToken
                )
            )
            val items = json.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val channelId = items.optJSONObject(i)
                        ?.optJSONObject("snippet")
                        ?.optJSONObject("resourceId")
                        ?.optString("channelId")
                    if (!channelId.isNullOrBlank()) result += channelId
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)

        return result.distinct()
    }

    private fun fetchOwnedPlaylists(token: String): List<RemotePlaylist> {
        val result = mutableListOf<RemotePlaylist>()
        var pageToken: String? = null

        do {
            val json = get(
                token,
                "playlists",
                mapOf(
                    "part" to "snippet",
                    "mine" to "true",
                    "maxResults" to MAX_RESULTS,
                    "pageToken" to pageToken
                )
            )
            val items = json.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val title = item.optJSONObject("snippet")?.optString("title")
                    if (id.isNotBlank() && !title.isNullOrBlank()) {
                        result += RemotePlaylist(id, title)
                    }
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)

        return result
    }

    private fun fetchLikesPlaylistId(token: String): String? {
        val json = get(
            token,
            "channels",
            mapOf(
                "part" to "contentDetails",
                "mine" to "true",
                "maxResults" to "1"
            )
        )
        return json.optJSONArray("items")
            ?.optJSONObject(0)
            ?.optJSONObject("contentDetails")
            ?.optJSONObject("relatedPlaylists")
            ?.optString("likes")
            ?.takeIf { it.isNotBlank() }
    }

    private fun fetchPlaylistItems(token: String, playlistId: String): List<String> {
        val result = mutableListOf<String>()
        var pageToken: String? = null

        do {
            val json = get(
                token,
                "playlistItems",
                mapOf(
                    "part" to "contentDetails",
                    "playlistId" to playlistId,
                    "maxResults" to MAX_RESULTS,
                    "pageToken" to pageToken
                )
            )
            val items = json.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val videoId = items.optJSONObject(i)
                        ?.optJSONObject("contentDetails")
                        ?.optString("videoId")
                    if (!videoId.isNullOrBlank()) result += videoId
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)

        return result.distinct()
    }

    private fun get(
        accessToken: String,
        endpoint: String,
        params: Map<String, String?>
    ): JSONObject {
        val builder = Uri.parse("$API_BASE/$endpoint").buildUpon()
        params.forEach { (key, value) ->
            if (!value.isNullOrBlank()) builder.appendQueryParameter(key, value)
        }

        val request = Request.Builder()
            .url(builder.build().toString())
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(body)
                        .optJSONObject("error")
                        ?.optString("message")
                }.getOrNull().orEmpty()
                throw IllegalStateException(
                    if (message.isBlank()) {
                        "YouTube Data API: HTTP ${response.code}"
                    } else {
                        "YouTube Data API: $message"
                    }
                )
            }
            return JSONObject(if (body.isBlank()) "{}" else body)
        }
    }
}

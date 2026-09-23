package com.github.libretube.helpers

import android.net.Uri
import com.github.libretube.LibreTubeApp
import com.github.libretube.R
import com.github.libretube.db.DatabaseHolder
import com.github.libretube.db.obj.LocalPlaylist
import com.github.libretube.db.obj.LocalPlaylistItem
import com.github.libretube.db.obj.LocalSubscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object YouTubeDirectImport {
    private const val API_BASE = "https://www.googleapis.com/youtube/v3"
    private const val MAX_RESULTS = "50"
    private const val PLAYLIST_PREF_PREFIX = "tubelite_youtube_playlist_map_"

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    data class ImportResult(
        val subscriptions: Int,
        val playlists: Int,
        val playlistVideos: Int,
        val likedVideos: Int,
        val failedPlaylists: Int = 0
    )

    private data class RemoteSubscription(
        val channelId: String,
        val title: String,
        val avatar: String?
    )

    private data class RemotePlaylist(
        val id: String,
        val title: String,
        val description: String?,
        val thumbnail: String?
    )

    private data class RemoteVideo(
        val id: String,
        val title: String?,
        val uploader: String?,
        val uploaderUrl: String?,
        val thumbnail: String?,
        val uploadDate: String?
    )

    suspend fun importAll(accessToken: String): ImportResult = withContext(Dispatchers.IO) {
        val subscriptions = fetchSubscriptions(accessToken)
        if (subscriptions.isNotEmpty()) {
            DatabaseHolder.Database.localSubscriptionDao().insertAll(
                subscriptions.map {
                    LocalSubscription(
                        channelId = it.channelId,
                        name = it.title,
                        avatar = it.avatar,
                        verified = false
                    )
                }
            )
        }

        PreferenceHelper.putLong("last_local_feed_refresh_timestamp_millis", 0L)

        val remotePlaylists = fetchOwnedPlaylists(accessToken)
        var playlistVideoCount = 0
        var importedPlaylistCount = 0
        var failedPlaylists = 0

        for (playlist in remotePlaylists) {
            val videos = runCatching {
                fetchPlaylistItems(accessToken, playlist.id)
            }.getOrElse {
                failedPlaylists++
                emptyList()
            }

            runCatching {
                upsertLocalPlaylist(playlist, videos)
            }.onSuccess {
                importedPlaylistCount++
                playlistVideoCount += videos.size
            }.onFailure {
                failedPlaylists++
            }
        }

        var likedCount = 0
        val likesPlaylistId = fetchLikesPlaylistId(accessToken)
        if (!likesPlaylistId.isNullOrBlank()) {
            val likes = runCatching {
                fetchPlaylistItems(accessToken, likesPlaylistId)
            }.getOrDefault(emptyList())
            likedCount = likes.size

            if (likes.isNotEmpty()) {
                runCatching {
                    upsertLocalPlaylist(
                        RemotePlaylist(
                            id = likesPlaylistId,
                            title = LibreTubeApp.instance.getString(R.string.yt_liked_videos),
                            description = LibreTubeApp.instance.getString(R.string.yt_imported_playlist),
                            thumbnail = likes.firstOrNull()?.thumbnail
                        ),
                        likes
                    )
                }
            }
        }

        WatchLaterHelper.getItems()

        ImportResult(
            subscriptions = subscriptions.size,
            playlists = importedPlaylistCount,
            playlistVideos = playlistVideoCount,
            likedVideos = likedCount,
            failedPlaylists = failedPlaylists
        )
    }

    private fun fetchSubscriptions(token: String): List<RemoteSubscription> {
        val result = mutableListOf<RemoteSubscription>()
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
                    val snippet = items.optJSONObject(i)?.optJSONObject("snippet") ?: continue
                    val channelId = snippet.optJSONObject("resourceId")
                        ?.optString("channelId")
                        .orEmpty()
                    if (channelId.isBlank()) continue

                    result += RemoteSubscription(
                        channelId = channelId,
                        title = snippet.optString("title").ifBlank { channelId },
                        avatar = bestThumbnail(snippet)
                    )
                }
            }

            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)

        return result.distinctBy { it.channelId }
    }

    private fun fetchOwnedPlaylists(token: String): List<RemotePlaylist> {
        val result = mutableListOf<RemotePlaylist>()
        var pageToken: String? = null

        do {
            val json = get(
                token,
                "playlists",
                mapOf(
                    "part" to "snippet,contentDetails",
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
                    val snippet = item.optJSONObject("snippet") ?: continue
                    val title = snippet.optString("title")
                    if (id.isBlank() || title.isBlank()) continue

                    result += RemotePlaylist(
                        id = id,
                        title = title,
                        description = snippet.optString("description").takeIf { it.isNotBlank() },
                        thumbnail = bestThumbnail(snippet)
                    )
                }
            }

            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)

        return result.distinctBy { it.id }
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

    private fun fetchPlaylistItems(token: String, playlistId: String): List<RemoteVideo> {
        val result = mutableListOf<RemoteVideo>()
        var pageToken: String? = null

        do {
            val json = get(
                token,
                "playlistItems",
                mapOf(
                    "part" to "snippet,contentDetails",
                    "playlistId" to playlistId,
                    "maxResults" to MAX_RESULTS,
                    "pageToken" to pageToken
                )
            )

            val items = json.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val snippet = item.optJSONObject("snippet")
                    val videoId = item.optJSONObject("contentDetails")
                        ?.optString("videoId")
                        .orEmpty()
                    if (videoId.isBlank()) continue

                    val uploaderId = snippet?.optString("videoOwnerChannelId").orEmpty()
                    result += RemoteVideo(
                        id = videoId,
                        title = snippet?.optString("title")?.takeIf {
                            it.isNotBlank() && it != "Private video" && it != "Deleted video"
                        },
                        uploader = snippet?.optString("videoOwnerChannelTitle")
                            ?.takeIf { it.isNotBlank() },
                        uploaderUrl = uploaderId.takeIf { it.isNotBlank() },
                        thumbnail = snippet?.let(::bestThumbnail),
                        uploadDate = snippet?.optString("publishedAt")
                            ?.takeIf { it.length >= 10 }
                            ?.substring(0, 10)
                    )
                }
            }

            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)

        return result.distinctBy { it.id }
    }

    private suspend fun upsertLocalPlaylist(
        playlist: RemotePlaylist,
        videos: List<RemoteVideo>
    ) {
        val dao = DatabaseHolder.Database.localPlaylistsDao()
        val prefs = LibreTubeApp.instance.getSharedPreferences(
            PLAYLIST_PREF_PREFIX + ProfileManager.getActiveProfileId(),
            0
        )

        val allBefore = dao.getAll()
        val mappedId = prefs.getInt(playlist.id, -1)
        val existing = if (mappedId > 0) {
            allBefore.firstOrNull { it.playlist.id == mappedId }
        } else {
            // Migration path for playlists imported by older TubeLite builds,
            // which did not persist the remote playlist id mapping.
            allBefore
                .filter { it.playlist.name.equals(playlist.title, ignoreCase = true) }
                .maxByOrNull { it.videos.size }
        }

        val localId = if (existing == null) {
            dao.createPlaylist(
                LocalPlaylist(
                    name = playlist.title,
                    thumbnailUrl = playlist.thumbnail.orEmpty(),
                    description = playlist.description
                )
            ).toInt()
        } else {
            existing.playlist.name = playlist.title
            existing.playlist.thumbnailUrl = playlist.thumbnail.orEmpty()
            existing.playlist.description = playlist.description
            dao.updatePlaylist(existing.playlist)
            dao.deletePlaylistItemsByPlaylistId(existing.playlist.id.toString())
            existing.playlist.id
        }

        videos.forEach { video ->
            dao.addPlaylistVideo(
                LocalPlaylistItem(
                    playlistId = localId,
                    videoId = video.id,
                    title = video.title,
                    uploadDate = video.uploadDate,
                    uploader = video.uploader,
                    uploaderUrl = video.uploaderUrl,
                    uploaderAvatar = null,
                    thumbnailUrl = video.thumbnail,
                    duration = null
                )
            )
        }

        prefs.edit().putInt(playlist.id, localId).apply()

        // Clean up empty duplicates left by pre-J7.7 imports, but never delete a
        // populated playlist merely because the user reused the same title.
        dao.getAll()
            .filter {
                it.playlist.id != localId &&
                    it.playlist.name.equals(playlist.title, ignoreCase = true) &&
                    it.videos.isEmpty()
            }
            .forEach { duplicate ->
                dao.deletePlaylistById(duplicate.playlist.id.toString())
            }
    }

    private fun bestThumbnail(snippet: JSONObject): String? {
        val thumbnails = snippet.optJSONObject("thumbnails") ?: return null
        return sequenceOf("maxres", "standard", "high", "medium", "default")
            .mapNotNull { key ->
                thumbnails.optJSONObject(key)?.optString("url")?.takeIf { it.isNotBlank() }
            }
            .firstOrNull()
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

        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        return JSONObject(if (body.isBlank()) "{}" else body)
                    }

                    val message = runCatching {
                        JSONObject(body)
                            .optJSONObject("error")
                            ?.optString("message")
                    }.getOrNull().orEmpty()

                    val error = IllegalStateException(
                        if (message.isBlank()) {
                            "YouTube Data API: HTTP ${response.code}"
                        } else {
                            "YouTube Data API: $message"
                        }
                    )

                    if (response.code !in 500..599 && response.code != 429) {
                        throw error
                    }
                    lastError = error
                }
            } catch (e: IOException) {
                lastError = e
            }

            if (attempt == 0) Thread.sleep(700)
        }

        throw lastError ?: IllegalStateException("YouTube Data API indisponível")
    }
}

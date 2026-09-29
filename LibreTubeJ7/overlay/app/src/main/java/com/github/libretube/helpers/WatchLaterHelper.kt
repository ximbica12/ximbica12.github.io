package com.github.libretube.helpers

import com.github.libretube.LibreTubeApp
import com.github.libretube.R
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.db.DatabaseHolder
import com.github.libretube.db.obj.LocalPlaylist
import com.github.libretube.db.obj.LocalPlaylistItem
import com.github.libretube.extensions.toID

object WatchLaterHelper {
    private const val LEGACY_MARKER = "__TUBELITE_WATCH_LATER__"
    private const val PREFS = "nexotube_watch_later"
    private const val KEY_PREFIX = "playlist_id_"

    private val prefs
        get() = LibreTubeApp.instance.getSharedPreferences(PREFS, 0)

    private suspend fun ensurePlaylistId(): Int {
        val dao = DatabaseHolder.Database.localPlaylistsDao()
        val prefKey = KEY_PREFIX + ProfileManager.getActiveProfileId()
        val mappedId = prefs.getInt(prefKey, -1)

        if (mappedId > 0 && dao.getAll().any { it.playlist.id == mappedId }) {
            return mappedId
        }

        // Migrate the old implementation that exposed its internal marker as the
        // playlist description in the Library UI.
        val legacy = dao.getAll().firstOrNull {
            it.playlist.description == LEGACY_MARKER
        }
        if (legacy != null) {
            legacy.playlist.name = LibreTubeApp.instance.getString(R.string.watch_later_local)
            legacy.playlist.description = null
            dao.updatePlaylist(legacy.playlist)
            prefs.edit().putInt(prefKey, legacy.playlist.id).apply()
            return legacy.playlist.id
        }

        val id = dao.createPlaylist(
            LocalPlaylist(
                name = LibreTubeApp.instance.getString(R.string.watch_later_local),
                thumbnailUrl = "",
                description = null
            )
        ).toInt()
        prefs.edit().putInt(prefKey, id).apply()
        return id
    }

    suspend fun contains(videoId: String): Boolean {
        val dao = DatabaseHolder.Database.localPlaylistsDao()
        val playlistId = ensurePlaylistId()
        return dao.getPlaylistVideo(playlistId.toString(), videoId) != null
    }

    suspend fun add(stream: StreamItem) {
        val videoId = stream.url?.toID() ?: return
        val dao = DatabaseHolder.Database.localPlaylistsDao()
        val playlistId = ensurePlaylistId()

        if (dao.getPlaylistVideo(playlistId.toString(), videoId) != null) return

        dao.addPlaylistVideo(
            LocalPlaylistItem(
                playlistId = playlistId,
                videoId = videoId,
                title = stream.title,
                uploadDate = stream.uploadedDate,
                uploader = stream.uploaderName,
                uploaderUrl = stream.uploaderUrl,
                uploaderAvatar = stream.uploaderAvatar,
                thumbnailUrl = stream.thumbnail,
                duration = stream.duration
            )
        )

        val relation = dao.getAll().firstOrNull { it.playlist.id == playlistId }
        if (relation != null && relation.playlist.thumbnailUrl.isBlank()) {
            relation.playlist.thumbnailUrl = stream.thumbnail.orEmpty()
            dao.updatePlaylist(relation.playlist)
        }
    }

    suspend fun remove(videoId: String) {
        val dao = DatabaseHolder.Database.localPlaylistsDao()
        val playlistId = ensurePlaylistId()
        dao.deletePlaylistItemsByVideoId(playlistId.toString(), videoId)
    }

    suspend fun getItems(): List<StreamItem> {
        val dao = DatabaseHolder.Database.localPlaylistsDao()
        val playlistId = ensurePlaylistId()
        return dao.getAll()
            .firstOrNull { it.playlist.id == playlistId }
            ?.videos
            ?.asReversed()
            ?.map { it.toStreamItem() }
            .orEmpty()
    }
}

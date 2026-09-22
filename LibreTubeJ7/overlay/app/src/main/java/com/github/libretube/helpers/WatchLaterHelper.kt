package com.github.libretube.helpers

import com.github.libretube.api.obj.StreamItem
import com.github.libretube.db.DatabaseHolder
import com.github.libretube.db.obj.LocalPlaylist
import com.github.libretube.db.obj.LocalPlaylistItem
import com.github.libretube.extensions.toID

object WatchLaterHelper {
    private const val MARKER = "__TUBELITE_WATCH_LATER__"
    private const val NAME = "Assistir mais tarde"

    private suspend fun ensurePlaylistId(): Int {
        val dao = DatabaseHolder.Database.localPlaylistsDao()
        val existing = dao.getAll().firstOrNull {
            it.playlist.description == MARKER
        }
        if (existing != null) return existing.playlist.id

        return dao.createPlaylist(
            LocalPlaylist(
                name = NAME,
                thumbnailUrl = "",
                description = MARKER
            )
        ).toInt()
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

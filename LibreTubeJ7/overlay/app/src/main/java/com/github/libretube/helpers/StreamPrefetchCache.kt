package com.github.libretube.helpers

import com.github.libretube.api.MediaServiceRepository
import com.github.libretube.api.obj.Streams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Small in-memory metadata/stream cache tuned for old devices.
 *
 * The expensive part before playback on YouTube is often extracting StreamInfo.
 * We keep only a handful of entries and prefetch sequentially, so the J7 gets
 * much faster click-to-play without keeping several decoders or large video
 * buffers alive.
 */
object StreamPrefetchCache {
    private const val MAX_ENTRIES = 6
    private const val TTL_MS = 8 * 60 * 1000L

    private data class Entry(
        val streams: Streams,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val lock = Any()
    private val fetchLocks = ConcurrentHashMap<String, Mutex>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cache = object : LinkedHashMap<String, Entry>(8, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Entry>?
        ): Boolean = size > MAX_ENTRIES
    }

    fun get(videoId: String): Streams? = synchronized(lock) {
        val entry = cache[videoId] ?: return@synchronized null
        if (System.currentTimeMillis() - entry.createdAt > TTL_MS) {
            cache.remove(videoId)
            null
        } else {
            entry.streams
        }
    }

    private fun put(videoId: String, streams: Streams) {
        synchronized(lock) {
            cache[videoId] = Entry(streams)
        }
    }

    suspend fun getOrFetch(videoId: String): Streams {
        get(videoId)?.let { return it }

        val mutex = fetchLocks.getOrPut(videoId) { Mutex() }
        return mutex.withLock {
            get(videoId)?.let { return@withLock it }

            val streams = MediaServiceRepository.instance.getStreams(videoId)
            put(videoId, streams)
            fetchLocks.remove(videoId)
            streams
        }
    }

    fun prefetch(videoIds: Iterable<String>, limit: Int = 3) {
        val ids = videoIds
            .filter { it.isNotBlank() }
            .distinct()
            .filter { get(it) == null }
            .take(limit)

        if (ids.isEmpty()) return

        scope.launch {
            // Sequential on purpose: old Exynos/J7 hardware gets worse when several
            // NewPipe extractions compete for CPU, TLS and memory at the same time.
            ids.forEach { id ->
                runCatching { getOrFetch(id) }
            }
        }
    }

    fun clear() = synchronized(lock) {
        cache.clear()
    }
}

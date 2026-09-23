package com.github.libretube.ui.models

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.libretube.api.MediaServiceRepository
import com.github.libretube.api.PlaylistsHelper
import com.github.libretube.api.SubscriptionHelper
import com.github.libretube.api.TrendingCategory
import com.github.libretube.api.obj.Playlists
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.db.DatabaseHelper
import com.github.libretube.db.DatabaseHolder
import com.github.libretube.db.obj.PlaylistBookmark
import com.github.libretube.extensions.runSafely
import com.github.libretube.extensions.updateIfChanged
import com.github.libretube.helpers.DiscoveryLanguageHelper
import com.github.libretube.helpers.PlayerHelper
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.helpers.StreamPrefetchCache
import com.github.libretube.helpers.WatchLaterHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

class HomeViewModel : ViewModel() {
    private val hideWatched
        get() = PreferenceHelper.getBoolean(
            PreferenceKeys.HIDE_WATCHED_FROM_FEED,
            false
        )

    private val showUpcoming
        get() = PreferenceHelper.getBoolean(
            PreferenceKeys.SHOW_UPCOMING_IN_FEED,
            true
        )

    val trending: MutableLiveData<Pair<TrendingCategory, TrendsViewModel.TrendingStreams>> =
        MutableLiveData(null)
    val feed: MutableLiveData<List<StreamItem>> = MutableLiveData(null)
    val bookmarks: MutableLiveData<List<PlaylistBookmark>> = MutableLiveData(null)
    val playlists: MutableLiveData<List<Playlists>> = MutableLiveData(null)
    val continueWatching: MutableLiveData<List<StreamItem>> = MutableLiveData(null)
    val watchLater: MutableLiveData<List<StreamItem>> = MutableLiveData(null)

    // Primary Home recommendation feed.
    val discovery: MutableLiveData<List<StreamItem>> = MutableLiveData(null)

    // New channels/topics in the dominant language inferred from recent viewing.
    val languageDiscovery: MutableLiveData<List<StreamItem>> = MutableLiveData(null)
    val discoveryLanguage: MutableLiveData<String> = MutableLiveData(null)

    val isLoading: MutableLiveData<Boolean> = MutableLiveData(true)
    val loadedSuccessfully: MutableLiveData<Boolean> = MutableLiveData(false)

    private val sections get() =
        listOf(feed, continueWatching, playlists, watchLater, discovery, languageDiscovery)

    var lastDiscoveryRegion: String? = null
        private set

    private var loadHomeJob: Job? = null

    fun loadHomeFeed(
        context: Context,
        subscriptionsViewModel: SubscriptionsViewModel,
        visibleItems: Set<String>,
        onUnusualLoadTime: () -> Unit
    ) {
        isLoading.value = true

        loadHomeJob?.cancel()
        loadHomeJob = viewModelScope.launch {
            val unusualTimer = launch(Dispatchers.IO) {
                delay(UNUSUAL_LOAD_TIME_MS)
                if (isLoading.value == true) onUnusualLoadTime.invoke()
            }

            // First paint: cheap/local/account sections. Do not make the whole Home
            // wait on recommendation extraction.
            awaitAll(
                async { loadFeed(subscriptionsViewModel) },
                async { loadVideosToContinueWatching() },
                async { loadPlaylists() },
                async { loadWatchLater() }
            )

            loadedSuccessfully.value = sections.any { !it.value.isNullOrEmpty() }
            isLoading.value = false
            unusualTimer.cancel()

            // Second paint: personalized discovery. It is intentionally allowed to
            // arrive section-by-section instead of freezing the screen.
            val region = PreferenceHelper.getTrendingRegion(context)
            lastDiscoveryRegion = region

            val history = if (PlayerHelper.watchHistoryEnabled) {
                runCatching { DatabaseHelper.getWatchHistoryPage(1, 24) }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val watchedIds = history.map { it.videoId }.toHashSet()

            val signals = withContext(Dispatchers.IO) {
                DiscoveryLanguageHelper.buildSignals(
                    recentVideoIds = history.map { it.videoId },
                    region = region
                )
            }
            discoveryLanguage.value = signals.language

            coroutineScope {
                launch { loadPersonalDiscovery(signals, subscriptionsViewModel, watchedIds) }
                launch { loadLanguageDiscovery(signals, watchedIds) }
                launch { loadRegionalDiscovery(region, watchedIds) }
            }

            loadedSuccessfully.value = sections.any { !it.value.isNullOrEmpty() }
        }
    }

    private suspend fun loadFeed(subscriptionsViewModel: SubscriptionsViewModel) {
        runSafely(
            onSuccess = { videos ->
                feed.updateIfChanged(videos)
                StreamPrefetchCache.prefetch(
                    videos.take(2).mapNotNull { it.url }
                )
            },
            ioBlock = { tryLoadFeed(subscriptionsViewModel) }
        )
    }

    private suspend fun loadPlaylists() {
        runSafely(
            onSuccess = { newPlaylists -> playlists.updateIfChanged(newPlaylists) },
            ioBlock = { PlaylistsHelper.getPlaylists() }
        )
    }

    private suspend fun loadWatchLater() {
        runSafely(
            onSuccess = { videos -> watchLater.updateIfChanged(videos) },
            ioBlock = { WatchLaterHelper.getItems() }
        )
    }

    private suspend fun loadPersonalDiscovery(
        signals: DiscoveryLanguageHelper.Signals,
        subscriptionsViewModel: SubscriptionsViewModel,
        watchedIds: Set<String>
    ) {
        val related = signals.seedStreams
            .flatMap { it.relatedStreams.take(10) }
            .filterNot { it.isShort }
            .distinctBy { it.url }
            .shuffled()

        val subscriptions = subscriptionsViewModel.videoFeed.value
            .orEmpty()
            .filterNot { it.isShort }
            .shuffled()

        // History/related dominates, but subscriptions only contribute a minority.
        val mixed = buildList {
            addAll(related.take(18))
            addAll(subscriptions.take(5))
        }
            .distinctBy { it.url }
            .filter { it.url !in watchedIds }

        val filtered = DatabaseHelper.filterByStreamTypeAndWatchPosition(
            mixed,
            hideWatched = true,
            showUpcoming = showUpcoming
        ).take(24)

        discovery.updateIfChanged(filtered)
        StreamPrefetchCache.prefetch(filtered.take(3).mapNotNull { it.url })
    }

    private suspend fun loadLanguageDiscovery(
        signals: DiscoveryLanguageHelper.Signals,
        watchedIds: Set<String>
    ) {
        val candidates = mutableListOf<StreamItem>()

        // Two searches are enough to diversify the feed without hammering the J7.
        for (query in signals.searchQueries.take(2)) {
            val results = withTimeoutOrNull(9_000L) {
                runCatching {
                    MediaServiceRepository.instance
                        .getSearchResults(query, "videos")
                        .items
                        .map { it.toStreamItem() }
                        .filterNot { it.isShort }
                }.getOrDefault(emptyList())
            }.orEmpty()

            candidates += results.take(12)
        }

        val languageResults = candidates
            .distinctBy { it.url }
            .filter { it.url !in watchedIds }
            .shuffled()
            .take(18)

        languageDiscovery.updateIfChanged(languageResults)
        StreamPrefetchCache.prefetch(languageResults.take(2).mapNotNull { it.url })
    }

    private suspend fun loadRegionalDiscovery(
        region: String,
        watchedIds: Set<String>
    ) {
        val categories = MediaServiceRepository.instance
            .getTrendingCategories()
            .filter { it != TrendingCategory.LIVE }

        val category = categories.randomOrNull() ?: return

        val regional = withTimeoutOrNull(10_000L) {
            runCatching {
                MediaServiceRepository.instance
                    .getTrending(region, category)
                    .filterNot { it.isShort }
                    .filter { it.url !in watchedIds }
                    .shuffled()
                    .take(14)
            }.getOrDefault(emptyList())
        }.orEmpty()

        trending.updateIfChanged(
            category to TrendsViewModel.TrendingStreams(region, regional)
        )
        StreamPrefetchCache.prefetch(regional.take(2).mapNotNull { it.url })
    }

    private suspend fun loadVideosToContinueWatching() {
        if (!PlayerHelper.watchHistoryEnabled) return

        runSafely(
            onSuccess = { videos -> continueWatching.updateIfChanged(videos) },
            ioBlock = ::loadWatchingFromDB
        )
    }

    private suspend fun loadWatchingFromDB(): List<StreamItem> {
        val videos = DatabaseHelper.getWatchHistoryPage(1, 20)
        return DatabaseHelper.filterUnwatched(videos.map { it.toStreamItem() })
    }

    private suspend fun tryLoadFeed(
        subscriptionsViewModel: SubscriptionsViewModel
    ): List<StreamItem> {
        val cached = subscriptionsViewModel.videoFeed.value
        val currentFeed = if (!cached.isNullOrEmpty()) {
            cached
        } else {
            SubscriptionHelper.getFeed(forceRefresh = false).also {
                subscriptionsViewModel.videoFeed.postValue(it)
            }
        }

        return DatabaseHelper.filterByStreamTypeAndWatchPosition(
            currentFeed,
            hideWatched,
            showUpcoming
        )
    }

    companion object {
        private const val UNUSUAL_LOAD_TIME_MS = 10000L
    }
}

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
import com.github.libretube.helpers.PlayerHelper
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.helpers.WatchLaterHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    val discovery: MutableLiveData<List<StreamItem>> = MutableLiveData(null)
    val isLoading: MutableLiveData<Boolean> = MutableLiveData(true)
    val loadedSuccessfully: MutableLiveData<Boolean> = MutableLiveData(false)

    private val sections get() =
        listOf(feed, continueWatching, playlists, watchLater, discovery)

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
            val result = async {
                awaitAll(
                    async { loadFeed(subscriptionsViewModel) },
                    async { loadVideosToContinueWatching() },
                    async { loadPlaylists() },
                    async { loadWatchLater() }
                )

                // Discovery comes after the cheap/local sections so it does not
                // compete with them for CPU/network on the Galaxy J7.
                loadDiscovery(context, subscriptionsViewModel)

                loadedSuccessfully.value = sections.any { !it.value.isNullOrEmpty() }
                isLoading.value = false
            }

            withContext(Dispatchers.IO) {
                delay(UNUSUAL_LOAD_TIME_MS)
                if (result.isActive) onUnusualLoadTime.invoke()
            }
        }
    }

    private suspend fun loadTrending(context: Context) {
        val region = PreferenceHelper.getTrendingRegion(context)
        val category = PreferenceHelper.getString(
            PreferenceKeys.TRENDING_CATEGORY,
            TrendingCategory.LIVE.name
        ).let { TrendingCategory.valueOf(it) }

        runSafely(
            onSuccess = { videos ->
                trending.updateIfChanged(
                    Pair(category, TrendsViewModel.TrendingStreams(region, videos))
                )
            },
            ioBlock = { MediaServiceRepository.instance.getTrending(region, category) }
        )
    }

    private suspend fun loadFeed(subscriptionsViewModel: SubscriptionsViewModel) {
        runSafely(
            onSuccess = { videos -> feed.updateIfChanged(videos) },
            ioBlock = { tryLoadFeed(subscriptionsViewModel) }
        )
    }

    private suspend fun loadBookmarks() {
        runSafely(
            onSuccess = { newBookmarks -> bookmarks.updateIfChanged(newBookmarks) },
            ioBlock = { DatabaseHolder.Database.playlistBookmarkDao().getAll() }
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

    private suspend fun loadDiscovery(
        context: Context,
        subscriptionsViewModel: SubscriptionsViewModel
    ) {
        runSafely(
            onSuccess = { videos -> discovery.updateIfChanged(videos) },
            ioBlock = {
                val history = if (PlayerHelper.watchHistoryEnabled) {
                    DatabaseHelper.getWatchHistoryPage(1, 20)
                } else {
                    emptyList()
                }

                val watchedIds = history.map { it.videoId }.toHashSet()
                val related = mutableListOf<StreamItem>()

                // About 60% of the recommendation pool starts from recent viewing.
                // Randomizing the seed selection prevents Home from becoming the same
                // two recommendation trees on every refresh.
                for (seed in history.shuffled().take(3)) {
                    val recommendations = withTimeoutOrNull(8_000L) {
                        runCatching {
                            MediaServiceRepository.instance
                                .getStreams(seed.videoId)
                                .relatedStreams
                                .filterNot { it.isShort }
                                .take(6)
                        }.getOrDefault(emptyList())
                    }.orEmpty()
                    related += recommendations
                }

                // Subscription feed contributes familiar creators.
                val subscriptions = subscriptionsViewModel.videoFeed.value
                    .orEmpty()
                    .filterNot { it.isShort }
                    .shuffled()
                    .take(8)

                // The remaining pool follows the region explicitly selected by the
                // user. One category/request keeps this inexpensive on the J7.
                val region = PreferenceHelper.getTrendingRegion(context)
                val categories = MediaServiceRepository.instance.getTrendingCategories()
                val preferred = PreferenceHelper.getString(
                    PreferenceKeys.TRENDING_CATEGORY,
                    TrendingCategory.LIVE.name
                )
                val category = categories.firstOrNull { it.name == preferred }
                    ?: categories.randomOrNull()

                val regional = if (category == null) {
                    emptyList()
                } else {
                    withTimeoutOrNull(10_000L) {
                        runCatching {
                            MediaServiceRepository.instance
                                .getTrending(region, category)
                                .filterNot { it.isShort }
                                .shuffled()
                                .take(8)
                        }.getOrDefault(emptyList())
                    }.orEmpty()
                }

                val mixed = buildList {
                    addAll(related.shuffled().take(12))
                    addAll(subscriptions.take(5))
                    addAll(regional.take(5))
                }
                    .distinctBy { it.url }
                    .filter { it.url !in watchedIds }

                DatabaseHelper.filterByStreamTypeAndWatchPosition(
                    mixed,
                    hideWatched = true,
                    showUpcoming = showUpcoming
                ).take(20)
            }
        )
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

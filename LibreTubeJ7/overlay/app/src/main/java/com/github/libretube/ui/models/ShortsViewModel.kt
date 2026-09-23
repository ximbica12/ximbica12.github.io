package com.github.libretube.ui.models

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.libretube.api.MediaServiceRepository
import com.github.libretube.api.SubscriptionHelper
import com.github.libretube.api.TrendingCategory
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.db.DatabaseHelper
import com.github.libretube.db.DatabaseHolder
import com.github.libretube.extensions.TAG
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.helpers.DiscoveryLanguageHelper
import com.github.libretube.helpers.StreamPrefetchCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ShortsViewModel : ViewModel() {
    val shorts = MutableLiveData<List<StreamItem>>(emptyList())
    val loading = MutableLiveData(false)

    private fun isShortCandidate(item: StreamItem): Boolean {
        val duration = item.duration ?: Long.MAX_VALUE
        return item.isShort || duration in 1..180
    }

    fun load(context: Context, forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            loading.postValue(true)

            val cachedFeed = DatabaseHolder.Database.feedDao()
                .getAll()
                .map { it.toStreamItem() }

            val cachedShorts = cachedFeed
                .filter(::isShortCandidate)
                .distinctBy { it.url }
                .shuffled()

            if (cachedShorts.isNotEmpty()) {
                shorts.postValue(cachedShorts.take(60))
            }

            val history = runCatching {
                DatabaseHelper.getWatchHistoryPage(1, 20)
            }.getOrDefault(emptyList())

            val watchedIds = history.map { it.videoId }.toHashSet()

            // Set the extractor language from what this profile actually watches,
            // rather than forcing the app UI language onto every Shorts search.
            val region = PreferenceHelper.getTrendingRegion(context)
            DiscoveryLanguageHelper.buildSignals(
                recentVideoIds = history.map { it.videoId },
                region = region
            )

            val personalized = mutableListOf<StreamItem>()

            // Related videos are the strongest taste signal available without using
            // YouTube's private Home feed.
            for (seed in history.shuffled().take(4)) {
                val related = withTimeoutOrNull(7_000L) {
                    runCatching {
                        MediaServiceRepository.instance
                            .getStreams(seed.videoId)
                            .relatedStreams
                            .filter(::isShortCandidate)
                            .take(8)
                    }.getOrDefault(emptyList())
                }.orEmpty()
                personalized += related
            }

            val refreshed = withTimeoutOrNull(20_000L) {
                runCatching {
                    SubscriptionHelper.getFeed(forceRefresh = forceRefresh)
                }.onFailure {
                    Log.e(TAG(), it.stackTraceToString())
                }.getOrNull()
            }.orEmpty()

            val subscriptionShorts = refreshed
                .filter(::isShortCandidate)
                .shuffled()
                .take(20)

            // Region-aware random discovery keeps Shorts from being limited to
            // subscriptions and prevents an empty page for new profiles.
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
                            .filter(::isShortCandidate)
                    }.getOrDefault(emptyList())
                }.orEmpty()
            }

            val result = buildList {
                addAll(personalized.shuffled().take(28))
                addAll(subscriptionShorts)
                addAll(regional.shuffled().take(16))
                addAll(cachedShorts)
            }
                .distinctBy { it.url }
                .filter { it.url !in watchedIds }
                .shuffled()
                .take(60)

            shorts.postValue(result)

            // Warm the currently visible Short and its immediate successors. This
            // removes most of the expensive extraction delay before autoplay.
            StreamPrefetchCache.prefetch(
                result.take(3).mapNotNull { it.url },
                limit = 3
            )

            loading.postValue(false)
        }
    }
}

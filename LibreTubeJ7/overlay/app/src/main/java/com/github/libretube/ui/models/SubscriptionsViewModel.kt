package com.github.libretube.ui.models

import android.content.Context
import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.libretube.R
import com.github.libretube.api.SubscriptionHelper
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.api.obj.Subscription
import com.github.libretube.db.DatabaseHolder
import com.github.libretube.db.obj.SubscriptionGroup
import com.github.libretube.extensions.TAG
import com.github.libretube.extensions.toastFromMainDispatcher
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.repo.FeedProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SubscriptionsViewModel : ViewModel() {
    var videoFeed = MutableLiveData<List<StreamItem>?>()

    var subscriptions = MutableLiveData<List<Subscription>?>()
    val feedProgress = MutableLiveData<FeedProgress?>()

    var subFeedRecyclerViewState: Parcelable? = null

    val groups = MutableLiveData<List<SubscriptionGroup>>()
    var groupToEdit: SubscriptionGroup? = null

    fun fetchFeed(context: Context, forceRefresh: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val videoFeed = try {
                SubscriptionHelper.getFeed(forceRefresh = forceRefresh) { feedProgress ->
                    this@SubscriptionsViewModel.feedProgress.postValue(feedProgress)
                }
            } catch (e: Exception) {
                context.toastFromMainDispatcher(R.string.server_error)
                Log.e(TAG(), e.toString())
                return@launch
            }
            this@SubscriptionsViewModel.videoFeed.postValue(videoFeed)
            videoFeed.firstOrNull { !it.isUpcoming }?.uploaded?.let {
                PreferenceHelper.updateLastFeedWatchedTime(it, false)
            }
        }
    }

    fun loadCachedSubscriptions() {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = DatabaseHolder.Database.localSubscriptionDao().getAll().map {
                Subscription(
                    url = it.channelId,
                    name = it.name ?: it.channelId,
                    avatar = it.avatar,
                    verified = it.verified
                )
            }.sortedBy { it.name.lowercase() }

            if (cached.isNotEmpty()) {
                subscriptions.postValue(cached)
            }
        }
    }

    fun fetchSubscriptions(context: Context) {
        loadCachedSubscriptions()

        viewModelScope.launch(Dispatchers.IO) {
            val fresh = try {
                withTimeoutOrNull(12_000L) {
                    SubscriptionHelper.getSubscriptions()
                }
            } catch (e: Exception) {
                Log.e(TAG(), e.toString())
                null
            }

            if (fresh == null) {
                // Keep showing the local list instead of blanking the screen when
                // YouTube RSS / channel metadata is temporarily unavailable.
                if (subscriptions.value.isNullOrEmpty()) {
                    context.toastFromMainDispatcher(R.string.server_error)
                }
                return@launch
            }

            this@SubscriptionsViewModel.subscriptions.postValue(fresh)
        }
    }
}

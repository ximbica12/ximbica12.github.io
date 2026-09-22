package com.github.libretube.ui.models

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.libretube.api.SubscriptionHelper
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.db.DatabaseHolder
import com.github.libretube.extensions.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ShortsViewModel : ViewModel() {
    val shorts = MutableLiveData<List<StreamItem>>(emptyList())
    val loading = MutableLiveData(false)

    fun load(context: Context, forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            loading.postValue(true)

            val cached = DatabaseHolder.Database.feedDao()
                .getAll()
                .map { it.toStreamItem() }
                .filter { it.isShort }
                .sortedByDescending { it.uploaded }
                .distinctBy { it.url }

            if (cached.isNotEmpty()) {
                shorts.postValue(cached.take(60))
            }

            val refreshed = runCatching {
                withTimeoutOrNull(25_000L) {
                    SubscriptionHelper.getFeed(forceRefresh = forceRefresh)
                }
            }.getOrElse {
                Log.e(TAG(), it.stackTraceToString())
                null
            }

            val freshShorts = refreshed.orEmpty()
                .filter { it.isShort }
                .sortedByDescending { it.uploaded }
                .distinctBy { it.url }

            if (freshShorts.isNotEmpty()) {
                shorts.postValue(freshShorts.take(60))
            } else if (cached.isEmpty()) {
                shorts.postValue(emptyList())
            }

            loading.postValue(false)
        }
    }
}

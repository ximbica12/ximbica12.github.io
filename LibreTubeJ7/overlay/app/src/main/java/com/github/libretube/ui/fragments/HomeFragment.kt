package com.github.libretube.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.R
import com.github.libretube.api.MediaServiceRepository
import com.github.libretube.api.TrendingCategory
import com.github.libretube.api.obj.Playlists
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.constants.PreferenceKeys.HOME_TAB_CONTENT
import com.github.libretube.databinding.FragmentHomeBinding
import com.github.libretube.db.obj.PlaylistBookmark
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.ui.activities.SettingsActivity
import com.github.libretube.ui.adapters.CarouselPlaylist
import com.github.libretube.ui.adapters.CarouselPlaylistAdapter
import com.github.libretube.ui.adapters.VideoCardsAdapter
import com.github.libretube.ui.models.HomeViewModel
import com.github.libretube.ui.models.SubscriptionsViewModel
import com.github.libretube.ui.models.TrendsViewModel
import com.google.android.material.carousel.CarouselLayoutManager
import com.google.android.material.carousel.CarouselSnapHelper
import com.google.android.material.carousel.UncontainedCarouselStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.Locale


class HomeFragment : Fragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val subscriptionsViewModel: SubscriptionsViewModel by activityViewModels()
    private val trendsViewModel: TrendsViewModel by activityViewModels()

    private val trendingAdapter = VideoCardsAdapter(columnWidthDp = 250f)
    private val feedAdapter = VideoCardsAdapter(columnWidthDp = 250f)
    private val watchingAdapter = VideoCardsAdapter(columnWidthDp = 250f)
    private val watchLaterAdapter = VideoCardsAdapter(columnWidthDp = 250f)
    private val discoveryAdapter = VideoCardsAdapter()
    private val languageDiscoveryAdapter = VideoCardsAdapter(columnWidthDp = 250f)
    private val bookmarkAdapter = CarouselPlaylistAdapter()
    private val playlistAdapter = CarouselPlaylistAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentHomeBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        binding.bookmarksRV.layoutManager = CarouselLayoutManager(UncontainedCarouselStrategy())
        binding.playlistsRV.layoutManager = CarouselLayoutManager(UncontainedCarouselStrategy())

        val bookmarksSnapHelper = CarouselSnapHelper()
        bookmarksSnapHelper.attachToRecyclerView(binding.bookmarksRV)

        val playlistsSnapHelper = CarouselSnapHelper()
        playlistsSnapHelper.attachToRecyclerView(binding.playlistsRV)

        binding.discoveryRV.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.VERTICAL,
            false
        )
        binding.languageDiscoveryRV.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )
        binding.trendingRV.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )
        binding.featuredRV.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )

        binding.discoveryRV.itemAnimator = null
        binding.languageDiscoveryRV.itemAnimator = null
        binding.trendingRV.itemAnimator = null
        binding.featuredRV.itemAnimator = null

        binding.trendingRV.adapter = trendingAdapter
        binding.featuredRV.adapter = feedAdapter
        binding.bookmarksRV.adapter = bookmarkAdapter
        binding.playlistsRV.adapter = playlistAdapter
        binding.playlistsRV.adapter?.registerAdapterDataObserver(object :
            RecyclerView.AdapterDataObserver() {
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                super.onItemRangeRemoved(positionStart, itemCount)
                if (itemCount == 0) {
                    binding.playlistsRV.isGone = true
                    binding.playlistsTV.isGone = true
                }
            }
        })
        binding.watchingRV.adapter = watchingAdapter
        binding.watchLaterRV.adapter = watchLaterAdapter
        binding.discoveryRV.adapter = discoveryAdapter
        binding.languageDiscoveryRV.adapter = languageDiscoveryAdapter

        // Personal sections first; the generic LibreTube discovery/trending layout
        // is intentionally de-emphasized for the J7 build.
        val sectionsParent = binding.featuredTV.parent as? android.view.ViewGroup
        sectionsParent?.let { parent ->
            val preferredOrder = listOf(
                binding.discoveryTV,
                binding.discoveryRV,
                binding.languageDiscoveryTV,
                binding.languageDiscoveryRV,
                binding.trendingTV,
                binding.trendingRV,
                binding.watchingTV,
                binding.watchingRV,
                binding.featuredTV,
                binding.featuredRV,
                binding.watchLaterTV,
                binding.watchLaterRV,
                binding.playlistsTV,
                binding.playlistsRV
            )
            preferredOrder.forEach { parent.removeView(it) }
            preferredOrder.forEach { parent.addView(it) }
        }
        binding.bookmarksTV.isGone = true
        binding.bookmarksRV.isGone = true

        with(homeViewModel) {
            trending.observe(viewLifecycleOwner, ::showTrending)
            feed.observe(viewLifecycleOwner, ::showFeed)
            bookmarks.observe(viewLifecycleOwner, ::showBookmarks)
            playlists.observe(viewLifecycleOwner, ::showPlaylists)
            continueWatching.observe(viewLifecycleOwner, ::showContinueWatching)
            watchLater.observe(viewLifecycleOwner, ::showWatchLater)
            discovery.observe(viewLifecycleOwner, ::showDiscovery)
            languageDiscovery.observe(viewLifecycleOwner, ::showLanguageDiscovery)
            discoveryLanguage.observe(viewLifecycleOwner, ::showDiscoveryLanguage)
            isLoading.observe(viewLifecycleOwner, ::updateLoading)
        }

        binding.featuredTV.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_subscriptionsFragment)
        }

        binding.watchingTV.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_watchHistoryFragment)
        }

        binding.watchLaterTV.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_libraryFragment)
        }

        binding.trendingTV.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_trendsFragment)
        }

        binding.playlistsTV.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_libraryFragment)
        }

        binding.bookmarksTV.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_libraryFragment)
        }

        binding.refresh.setOnRefreshListener {
            binding.refresh.isRefreshing = true
            fetchHomeFeed()
        }

        binding.trendingRegion.setOnClickListener {
            TrendsFragment.showChangeRegionDialog(requireContext()) {
                fetchHomeFeed()
            }
        }

        val trendingCategories = MediaServiceRepository.instance.getTrendingCategories()
        binding.trendingCategory.isGone = true
        binding.trendingCategory.setOnClickListener {
            val currentTrendingCategoryPref = PreferenceHelper.getString(
                PreferenceKeys.TRENDING_CATEGORY,
                TrendingCategory.LIVE.name
            ).let { categoryName -> trendingCategories.first { it.name == categoryName } }

            val categories = trendingCategories.map { category ->
                category to getString(category.titleRes)
            }

            var selected = trendingCategories.indexOf(currentTrendingCategoryPref)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.category)
                .setSingleChoiceItems(
                    categories.map { it.second }.toTypedArray(),
                    selected
                ) { _, checked ->
                    selected = checked
                }
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.okay) { _, _ ->
                    PreferenceHelper.putString(
                        PreferenceKeys.TRENDING_CATEGORY,
                        trendingCategories[selected].name
                    )
                    fetchHomeFeed()
                }
                .show()
        }

        binding.refreshButton.setOnClickListener {
            fetchHomeFeed()
        }

        binding.changeInstance.setOnClickListener {
            redirectToIntentSettings()
        }
    }

    override fun onResume() {
        super.onResume()

        // Avoid re-fetching when re-entering the screen if it was loaded successfully, except when
        // the value of trending region has changed
        val isRegionChanged =
            homeViewModel.lastDiscoveryRegion != null &&
                homeViewModel.lastDiscoveryRegion != PreferenceHelper.getTrendingRegion(requireContext())

        if (homeViewModel.loadedSuccessfully.value == false || isRegionChanged) {
            fetchHomeFeed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun fetchHomeFeed() {
        binding.nothingHere.isGone = true
        val defaultItems = resources.getStringArray(R.array.homeTabItemsValues)
        val visibleItems = PreferenceHelper.getStringSet(HOME_TAB_CONTENT, defaultItems.toSet())

        homeViewModel.loadHomeFeed(
            context = requireContext(),
            subscriptionsViewModel = subscriptionsViewModel,
            visibleItems = visibleItems,
            onUnusualLoadTime = ::showChangeInstanceSnackBar
        )
    }

    private fun showTrending(trends: Pair<TrendingCategory, TrendsViewModel.TrendingStreams>?) {
        if (trends == null) return
        val (category, trendingStreams) = trends

        // cache the loaded trends in the [TrendsViewModel] so that the trends don't need to be
        // reloaded there
        val region = PreferenceHelper.getTrendingRegion(requireContext())
        trendsViewModel.setStreamsForCategory(
            category,
            TrendsViewModel.TrendingStreams(region, trendingStreams.streams)
        )

        if (trendingStreams.streams.isEmpty()) {
            binding.trendingTV.isGone = true
            binding.trendingRV.isGone = true
            return
        }

        makeVisible(binding.trendingRV, binding.trendingTV)
        trendingAdapter.submitList(trendingStreams.streams.take(14))
    }

    private fun showFeed(streamItems: List<StreamItem>?) {
        if (streamItems == null) return

        if (streamItems.isEmpty()) {
            binding.featuredTV.isGone = true
            binding.featuredRV.isGone = true
            return
        }

        makeVisible(binding.featuredRV, binding.featuredTV)
        feedAdapter.submitList(streamItems.take(10))
    }

    private fun showBookmarks(bookmarks: List<PlaylistBookmark>?) {
        if (bookmarks == null) return

        makeVisible(binding.bookmarksTV, binding.bookmarksRV)
        bookmarkAdapter.submitList(bookmarks.map { bookmark ->
            CarouselPlaylist(
                id = bookmark.playlistId,
                title = bookmark.playlistName,
                thumbnail = bookmark.thumbnailUrl
            )
        })
    }

    private fun showPlaylists(playlists: List<Playlists>?) {
        if (playlists == null) return

        makeVisible(binding.playlistsRV, binding.playlistsTV)
        playlistAdapter.submitList(playlists.map { playlist ->
            CarouselPlaylist(
                id = playlist.id!!,
                thumbnail = playlist.thumbnail,
                title = playlist.name
            )
        })
    }

    private fun showContinueWatching(unwatchedVideos: List<StreamItem>?) {
        if (unwatchedVideos == null) return

        makeVisible(binding.watchingRV, binding.watchingTV)
        watchingAdapter.submitList(unwatchedVideos)
    }

    private fun showWatchLater(videos: List<StreamItem>?) {
        if (videos.isNullOrEmpty()) {
            binding.watchLaterTV.isGone = true
            binding.watchLaterRV.isGone = true
            return
        }

        makeVisible(binding.watchLaterTV, binding.watchLaterRV)
        watchLaterAdapter.submitList(videos.take(20))
    }

    private fun showDiscovery(videos: List<StreamItem>?) {
        if (videos.isNullOrEmpty()) {
            binding.discoveryTV.isGone = true
            binding.discoveryRV.isGone = true
            return
        }

        makeVisible(binding.discoveryTV, binding.discoveryRV)
        discoveryAdapter.submitList(videos.take(8))
    }

    private fun showLanguageDiscovery(videos: List<StreamItem>?) {
        if (videos.isNullOrEmpty()) {
            binding.languageDiscoveryTV.isGone = true
            binding.languageDiscoveryRV.isGone = true
            return
        }

        makeVisible(binding.languageDiscoveryTV, binding.languageDiscoveryRV)
        languageDiscoveryAdapter.submitList(videos.take(16))
    }

    private fun showDiscoveryLanguage(languageCode: String?) {
        if (languageCode.isNullOrBlank()) return
        val locale = Locale.forLanguageTag(languageCode)
        val display = locale.getDisplayLanguage(Locale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        binding.languageDiscoveryTitle.text =
            getString(R.string.home_language_discovery, display)
    }

    private fun updateLoading(isLoading: Boolean) {
        if (isLoading) {
            showLoading()
        } else {
            hideLoading()
        }
    }

    private fun showLoading() {
        val hasExistingContent = homeViewModel.loadedSuccessfully.value == true
        binding.progress.isVisible = !hasExistingContent && !binding.refresh.isRefreshing
        binding.nothingHere.isVisible = false
        binding.scroll.isVisible = hasExistingContent
        // Keep already-loaded content fully interactive while refreshing. Dimming
        // the entire page made Home feel blocked even when useful data was present.
        binding.scroll.alpha = 1.0f
    }

    private fun hideLoading() {
        binding.progress.isVisible = false
        binding.refresh.isRefreshing = false

        val hasContent = homeViewModel.loadedSuccessfully.value == true
        if (hasContent) {
            showContent()
        } else {
            showNothingHere()
        }
        binding.scroll.alpha = 1.0f
    }

    private fun showNothingHere() {
        binding.nothingHere.isVisible = true
        binding.scroll.isVisible = false
    }

    private fun showContent() {
        binding.nothingHere.isVisible = false
        binding.scroll.isVisible = true
    }

    private fun showChangeInstanceSnackBar() {
        val root = _binding?.root ?: return
        Snackbar
            .make(root, R.string.suggest_change_instance, Snackbar.LENGTH_LONG)
            .apply {
                setAction(R.string.change) {
                    redirectToIntentSettings()
                }
                show()
            }
    }

    private fun redirectToIntentSettings() {
        val settingsIntent = Intent(context, SettingsActivity::class.java).apply {
            putExtra(SettingsActivity.REDIRECT_KEY, SettingsActivity.REDIRECT_TO_INTENT_SETTINGS)
        }
        startActivity(settingsIntent)
    }

    private fun makeVisible(vararg views: View) {
        views.forEach { it.isVisible = true }
    }
}

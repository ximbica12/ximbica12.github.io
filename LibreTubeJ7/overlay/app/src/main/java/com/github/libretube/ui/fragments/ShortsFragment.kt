package com.github.libretube.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.R
import com.github.libretube.api.obj.Streams
import com.github.libretube.databinding.FragmentShortsBinding
import com.github.libretube.extensions.toID
import com.github.libretube.helpers.PlayerHelper
import com.github.libretube.helpers.ProxyHelper
import com.github.libretube.helpers.StreamPrefetchCache
import com.github.libretube.player.SabrMediaSource
import com.github.libretube.player.manifest.SabrManifest
import com.github.libretube.ui.adapters.ShortsFeedAdapter
import com.github.libretube.ui.models.ShortsViewModel
import com.github.libretube.util.DefaultTrackSelectorWithAudioQualitySupport
import com.github.libretube.util.YoutubeHlsPlaylistParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@UnstableApi
class ShortsFragment : Fragment(R.layout.fragment_shorts) {
    private var _binding: FragmentShortsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShortsViewModel by viewModels()
    private val snapHelper = PagerSnapHelper()

    private val adapter = ShortsFeedAdapter { position, _ ->
        if (position == currentIndex) {
            shortsPlayer?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
            }
        } else {
            binding.shortsList.smoothScrollToPosition(position)
        }
    }

    private var shortsPlayer: ExoPlayer? = null
    private var sharedPlayerView: PlayerView? = null
    private var playJob: Job? = null
    private var currentIndex = RecyclerView.NO_POSITION

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentShortsBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        val layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.VERTICAL,
            false
        )
        binding.shortsList.layoutManager = layoutManager
        binding.shortsList.adapter = adapter
        binding.shortsList.itemAnimator = null
        binding.shortsList.setItemViewCacheSize(2)

        if (binding.shortsList.onFlingListener == null) {
            snapHelper.attachToRecyclerView(binding.shortsList)
        }

        createShortsPlayer()

        binding.shortsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    playSnappedShort()
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    shortsPlayer?.pause()
                }
            }
        })

        viewModel.shorts.observe(viewLifecycleOwner) { shorts ->
            adapter.submitList(shorts) {
                binding.shortsEmpty.isVisible =
                    shorts.isEmpty() && viewModel.loading.value != true

                if (shorts.isNotEmpty()) {
                    binding.shortsList.post { playSnappedShort() }
                }
            }
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.shortsRefresh.isRefreshing = loading
            binding.shortsProgress.isVisible = loading && adapter.currentList.isEmpty()
            binding.shortsEmpty.isVisible = !loading && adapter.currentList.isEmpty()
        }

        binding.shortsRefresh.setOnRefreshListener {
            viewModel.load(requireContext(), forceRefresh = true)
        }

        binding.shortsRetry.setOnClickListener {
            viewModel.load(requireContext(), forceRefresh = true)
        }

        viewModel.load(requireContext(), forceRefresh = false)
    }

    private fun createShortsPlayer() {
        val trackSelector = DefaultTrackSelectorWithAudioQualitySupport(requireContext()).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(1280, 720)
                    .setForceHighestSupportedBitrate(false)
            )
        }

        shortsPlayer = PlayerHelper.createPlayer(requireContext(), trackSelector).apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    currentHolder()?.loading?.isVisible =
                        playbackState == Player.STATE_BUFFERING
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) currentHolder()?.loading?.isVisible = false
                }
            })
        }

        sharedPlayerView = PlayerView(requireContext()).apply {
            player = shortsPlayer
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setShutterBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun playSnappedShort() {
        val layoutManager = binding.shortsList.layoutManager ?: return
        val snapped = snapHelper.findSnapView(layoutManager) ?: return
        val position = binding.shortsList.getChildAdapterPosition(snapped)
        if (position == RecyclerView.NO_POSITION) return
        playAt(position)
    }

    private fun playAt(position: Int) {
        val item = adapter.currentList.getOrNull(position) ?: return
        val videoId = item.url.orEmpty().toID()
        if (videoId.isBlank()) return

        if (position == currentIndex && shortsPlayer?.currentMediaItem != null) {
            attachSharedPlayer(position)
            shortsPlayer?.play()
            prefetchAround(position)
            return
        }

        currentIndex = position
        attachSharedPlayer(position)
        currentHolder()?.loading?.isVisible = true

        playJob?.cancel()
        playJob = viewLifecycleOwner.lifecycleScope.launch {
            val streams = withContext(Dispatchers.IO) {
                withTimeoutOrNull(20_000L) {
                    runCatching {
                        StreamPrefetchCache.getOrFetch(videoId)
                    }.getOrNull()
                }
            }

            if (streams == null || currentIndex != position || _binding == null) {
                currentHolder()?.loading?.isVisible = false
                return@launch
            }

            setShortMediaSource(videoId, streams)
            shortsPlayer?.apply {
                prepare()
                playWhenReady = true
                play()
            }

            prefetchAround(position)
        }
    }

    private fun attachSharedPlayer(position: Int) {
        val holder = binding.shortsList.findViewHolderForAdapterPosition(position)
            as? ShortsFeedAdapter.Holder ?: return
        val playerView = sharedPlayerView ?: return

        (playerView.parent as? ViewGroup)?.removeView(playerView)
        holder.playerHost.removeAllViews()
        holder.playerHost.addView(playerView)
    }

    private fun currentHolder(): ShortsFeedAdapter.Holder? =
        binding.shortsList.findViewHolderForAdapterPosition(currentIndex)
            as? ShortsFeedAdapter.Holder

    private fun prefetchAround(position: Int) {
        val ids = listOfNotNull(
            adapter.currentList.getOrNull(position + 1)?.url?.toID(),
            adapter.currentList.getOrNull(position + 2)?.url?.toID(),
            adapter.currentList.getOrNull(position - 1)?.url?.toID()
        )
        StreamPrefetchCache.prefetch(ids, limit = 3)
    }

    private fun setShortMediaSource(videoId: String, streams: Streams) {
        val player = shortsPlayer ?: return
        player.stop()
        player.clearMediaItems()

        when {
            !streams.isLive &&
                streams.serverAbrStreamingUrl != null &&
                streams.videoPlaybackUstreamerConfig != null -> {
                val source = SabrMediaSource.Factory(
                    SabrManifest(videoId, streams)
                ).createMediaSource(
                    MediaItem.Builder()
                        .setUri(streams.serverAbrStreamingUrl.toUri())
                        .setMimeType("application/vnd.yt-ump")
                        .build()
                )
                player.setMediaSource(source)
            }

            streams.videoStreams.any { it.url?.startsWith("sabr://") != true } -> {
                val playable = streams.copy(
                    videoStreams = streams.videoStreams.filter {
                        it.url?.startsWith("sabr://") != true
                    }
                )
                player.setMediaItem(
                    MediaItem.Builder()
                        .setUri(PlayerHelper.createDashSource(playable, requireContext()))
                        .setMimeType(MimeTypes.APPLICATION_MPD)
                        .build()
                )
            }

            streams.hls != null -> {
                val source = HlsMediaSource.Factory(
                    DefaultDataSource.Factory(requireContext())
                )
                    .setPlaylistParserFactory(YoutubeHlsPlaylistParser.Factory())
                    .createMediaSource(
                        MediaItem.Builder()
                            .setUri(
                                ProxyHelper.rewriteUrlUsingProxyPreference(streams.hls).toUri()
                            )
                            .setMimeType(MimeTypes.APPLICATION_M3U8)
                            .build()
                    )
                player.setMediaSource(source)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            binding.shortsList.post { playSnappedShort() }
        }
    }

    override fun onPause() {
        shortsPlayer?.pause()
        super.onPause()
    }

    override fun onDestroyView() {
        playJob?.cancel()
        playJob = null

        sharedPlayerView?.player = null
        (sharedPlayerView?.parent as? ViewGroup)?.removeView(sharedPlayerView)
        sharedPlayerView = null

        shortsPlayer?.release()
        shortsPlayer = null
        currentIndex = RecyclerView.NO_POSITION

        super.onDestroyView()
        _binding = null
    }
}

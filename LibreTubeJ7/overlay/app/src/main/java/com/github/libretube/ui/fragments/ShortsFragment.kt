package com.github.libretube.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.github.libretube.R
import com.github.libretube.databinding.FragmentShortsBinding
import com.github.libretube.ui.adapters.VideoCardsAdapter
import com.github.libretube.ui.models.ShortsViewModel

class ShortsFragment : Fragment(R.layout.fragment_shorts) {
    private var _binding: FragmentShortsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShortsViewModel by viewModels()
    private val adapter = VideoCardsAdapter(columnWidthDp = 165f)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentShortsBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        binding.shortsList.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.shortsList.adapter = adapter

        viewModel.shorts.observe(viewLifecycleOwner) { shorts ->
            adapter.submitList(shorts)
            binding.shortsEmpty.isVisible = shorts.isEmpty() && viewModel.loading.value != true
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

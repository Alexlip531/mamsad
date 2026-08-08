package com.zai.mamsad.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.zai.mamsad.R
import com.zai.mamsad.databinding.FragmentDetailBinding
import com.zai.mamsad.ui.CatalogViewModel
import kotlinx.coroutines.launch

class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CatalogViewModel by activityViewModels { CatalogViewModel.Factory }

    private val orgId: Int by lazy {
        arguments?.getInt("orgId") ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.btnOpenSite.setOnClickListener {
            val link = binding.tvSourceLink.tag as? String ?: return@setOnClickListener
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            startActivity(Intent.createChooser(intent, "Открыть в браузере"))
        }

        binding.btnFavorite.setOnClickListener {
            // Toggle favorited state — observed via Room Flow
            val isFav = binding.btnFavorite.tag as? Boolean ?: false
            viewModel.setFavorite(orgId, !isFav)
        }

        binding.btnShare.setOnClickListener {
            shareCurrent()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.getById(orgId).collect { org ->
                    if (org == null) {
                        // Not in cache yet — wait for refresh
                        return@collect
                    }
                    binding.toolbar.title = org.title
                    binding.tvDetailTitle.text = org.title
                    binding.tvDetailCity.text = org.cityName
                    binding.tvDetailType.text = org.typeName
                    binding.tvDetailDescription.text = org.excerpt.ifBlank {
                        org.content.replace(Regex("<[^>]*>"), "").trim()
                    }
                    binding.tvSourceLink.text = org.link.removePrefix("https://")
                    binding.tvSourceLink.tag = org.link

                    // Tags (categories)
                    if (org.categoryNames.isNotBlank()) {
                        binding.sectionTags.isVisible = true
                        binding.chipsTags.removeAllViews()
                        org.categoryNames.split(",").forEach { name ->
                            val chip = Chip(requireContext())
                            chip.text = name.trim()
                            chip.isClickable = false
                            chip.setChipBackgroundColorResource(R.color.mamsad_cream_dark)
                            binding.chipsTags.addView(chip)
                        }
                    } else {
                        binding.sectionTags.isVisible = false
                    }

                    // Favorite button state
                    val isFav = org.isFavorite
                    binding.btnFavorite.tag = isFav
                    binding.btnFavorite.isSelected = isFav
                    binding.btnFavorite.text = getString(
                        if (isFav) R.string.detail_btn_unfavorite else R.string.detail_btn_favorite
                    )
                }
            }
        }
    }

    private fun shareCurrent() {
        val title = binding.tvDetailTitle.text.toString()
        val link = binding.tvSourceLink.tag as? String ?: "https://mamsad.ru/"
        val text = getString(R.string.detail_share_template, title, link)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.detail_btn_share)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

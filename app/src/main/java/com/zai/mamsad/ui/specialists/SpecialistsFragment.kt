package com.zai.mamsad.ui.specialists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.zai.mamsad.R
import com.zai.mamsad.api.WpTerm
import com.zai.mamsad.databinding.FragmentSpecialistsBinding
import com.zai.mamsad.ui.CatalogViewModel
import kotlinx.coroutines.launch

/**
 * Specialists screen — matches https://mamsad.ru/speczialisty/
 *
 * Shows a grid of specialist categories (Няни, Логопеды, Психологи...).
 * Tapping a category opens the Catalog filtered by that category.
 */
class SpecialistsFragment : Fragment() {

    private var _binding: FragmentSpecialistsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CatalogViewModel by activityViewModels { CatalogViewModel.Factory }

    private lateinit var adapter: SpecialistsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpecialistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        adapter = SpecialistsAdapter { item -> openCategory(item) }
        binding.rvSpecialists.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvSpecialists.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.categories.collect { terms ->
                    adapter.submitList(filterSpecialists(terms))
                }
            }
        }
    }

    /**
     * Filter mamsad.ru categories down to specialist-type ones (by slug).
     * Returns the curated list, ordered by relevance.
     */
    private fun filterSpecialists(terms: List<WpTerm>): List<SpecialistItem> {
        // Map slug -> emoji (kept in a curated order for the grid)
        val order = listOf(
            "nyani" to "👶",
            "bebisittery" to "🧸",
            "logopedy" to "🗣️",
            "psihologi" to "🧠",
            "repetitory" to "📚",
            "detskie-vrachi" to "🩺",
            "animatory" to "🎉",
            "fotografy" to "📸"
        )
        val bySlug = terms.associateBy { it.slug }
        return order.mapNotNull { (slug, emoji) ->
            bySlug[slug]?.let { SpecialistItem(categoryId = it.id, name = it.name, emoji = emoji) }
        }
    }

    private fun openCategory(item: SpecialistItem) {
        // Apply category filter and navigate to catalog
        viewModel.resetFilters()
        viewModel.toggleCategory(item.categoryId)
        findNavController().navigate(R.id.action_specialists_to_catalog)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvSpecialists.adapter = null
        _binding = null
    }
}

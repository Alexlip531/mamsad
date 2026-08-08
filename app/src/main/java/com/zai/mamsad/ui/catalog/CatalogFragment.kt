package com.zai.mamsad.ui.catalog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.zai.mamsad.R
import com.zai.mamsad.api.WpTerm
import com.zai.mamsad.databinding.FragmentCatalogBinding
import com.zai.mamsad.ui.CatalogViewModel
import kotlinx.coroutines.launch

class CatalogFragment : Fragment() {

    private var _binding: FragmentCatalogBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CatalogViewModel by activityViewModels { CatalogViewModel.Factory }

    private lateinit var adapter: KindergartenAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCatalogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = KindergartenAdapter(
            onClick = { org ->
                val args = Bundle().apply { putInt("orgId", org.id) }
                findNavController().navigate(R.id.action_catalog_to_detail, args)
            },
            onFavorite = { org, fav -> viewModel.setFavorite(org.id, fav) }
        )

        binding.rvCatalog.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCatalog.adapter = adapter

        // Search
        binding.etSearch.setText(viewModel.filter.value.searchQuery)
        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            viewModel.setSearch(text?.toString() ?: "")
        }

        // Filter button
        binding.btnFilters.setOnClickListener {
            findNavController().navigate(R.id.action_catalog_to_filters)
        }

        // Retry / clear filters
        binding.btnRetry.setOnClickListener { viewModel.refresh() }
        binding.btnClearFilters.setOnClickListener {
            viewModel.resetFilters()
            binding.etSearch.text?.clear()
        }

        // Swipe to refresh
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.swipeRefresh.setColorSchemeResources(R.color.mamsad_coral, R.color.mamsad_sage)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeCatalog() }
                launch { observeFilterAndTaxonomies() }
                launch { observeRefreshing() }
                launch { observeError() }
            }
        }
    }

    private suspend fun observeCatalog() {
        viewModel.filteredOrgs.collect { items ->
            adapter.submitList(items)

            val hasData = items.isNotEmpty()
            binding.tvResultsCount.visibility = if (hasData) View.VISIBLE else View.GONE
            binding.tvResultsCount.text = resources.getQuantityString(
                R.plurals.kindergartens_found,
                items.size,
                items.size
            )

            // Show empty state if we have initial data and no results
            val showEmpty = viewModel.hasInitialData.value && items.isEmpty()
            binding.stateEmpty.visibility = if (showEmpty) View.VISIBLE else View.GONE

            // Hide list when empty/loading
            binding.rvCatalog.visibility =
                if (hasData) View.VISIBLE else View.GONE
        }
    }

    private suspend fun observeFilterAndTaxonomies() {
        // Re-render chips whenever filter changes OR taxonomy terms load
        kotlinx.coroutines.flow.combine(
            viewModel.filter,
            viewModel.cities,
            viewModel.types,
            viewModel.categories
        ) { f, cities, types, categories -> Quad(f, cities, types, categories) }
            .collect { quad ->
                val f = quad.f
                val cities = quad.cities
                val types = quad.types
                val categories = quad.categories
                binding.chipsActive.removeAllViews()
                if (f.isActive) {
                    binding.scrollActiveFilters.visibility = View.VISIBLE
                    if (f.searchQuery.isNotBlank()) addRemovableChip("«${f.searchQuery}»") {
                        viewModel.setSearch("")
                        binding.etSearch.text?.clear()
                    }
                    f.cityId?.let { id ->
                        cities.firstOrNull { it.id == id }?.let { c ->
                            addRemovableChip(c.name) { viewModel.setCity(null) }
                        }
                    }
                    f.typeId?.let { id ->
                        types.firstOrNull { it.id == id }?.let { t ->
                            addRemovableChip(t.name) { viewModel.setType(null) }
                        }
                    }
                    f.categoryIds.forEach { id ->
                        categories.firstOrNull { it.id == id }?.let { cat ->
                            addRemovableChip(cat.name) { viewModel.toggleCategory(id) }
                        }
                    }
                } else {
                    binding.scrollActiveFilters.visibility = View.GONE
                }
            }
    }

    private data class Quad(
        val f: com.zai.mamsad.data.CatalogFilter,
        val cities: List<WpTerm>,
        val types: List<WpTerm>,
        val categories: List<WpTerm>
    )

    private fun addRemovableChip(label: String, onRemove: () -> Unit) {
        val chip = Chip(requireContext())
        chip.text = label
        chip.isCloseIconVisible = true
        chip.setChipBackgroundColorResource(R.color.mamsad_coral_light)
        chip.setTextColor(resources.getColor(R.color.mamsad_coral_dark, null))
        chip.isClickable = true
        chip.setOnClickListener { onRemove() }
        binding.chipsActive.addView(chip)
    }

    private suspend fun observeRefreshing() {
        viewModel.isRefreshing.collect { refreshing ->
            binding.swipeRefresh.isRefreshing = refreshing
            val showLoading = refreshing && viewModel.hasInitialData.value.not()
            binding.stateLoading.visibility = if (showLoading) View.VISIBLE else View.GONE
        }
    }

    private suspend fun observeError() {
        viewModel.refreshError.collect { err ->
            val show = err != null && viewModel.hasInitialData.value.not()
            binding.stateError.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvCatalog.adapter = null
        _binding = null
    }
}

package com.zai.mamsad.ui.filters

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
import com.google.android.material.chip.Chip
import com.zai.mamsad.R
import com.zai.mamsad.data.SortMode
import com.zai.mamsad.databinding.FragmentFiltersBinding
import com.zai.mamsad.ui.CatalogViewModel
import kotlinx.coroutines.launch

class FiltersFragment : Fragment() {

    private var _binding: FragmentFiltersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CatalogViewModel by activityViewModels { CatalogViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Pre-fill from current filter state
        binding.etSearchName.setText(viewModel.filter.value.searchQuery)
        when (viewModel.filter.value.sort) {
            SortMode.NEW -> binding.rbSortNew.isChecked = true
            SortMode.ALPHA -> binding.rbSortAlpha.isChecked = true
            SortMode.CITY -> binding.rbSortCity.isChecked = true
            SortMode.RATING -> binding.rbSortRating.isChecked = true
        }

        // Search input — apply live
        binding.etSearchName.doOnTextChanged { text, _, _, _ ->
            viewModel.setSearch(text?.toString() ?: "")
        }

        // Sort radio
        binding.rgSort.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_sort_new -> viewModel.setSort(SortMode.NEW)
                R.id.rb_sort_alpha -> viewModel.setSort(SortMode.ALPHA)
                R.id.rb_sort_city -> viewModel.setSort(SortMode.CITY)
                R.id.rb_sort_rating -> viewModel.setSort(SortMode.RATING)
            }
        }

        // Buttons
        binding.btnApply.setOnClickListener {
            findNavController().navigate(R.id.action_filters_to_catalog)
        }
        binding.btnReset.setOnClickListener {
            viewModel.resetFilters()
            binding.etSearchName.text?.clear()
            binding.rbSortNew.isChecked = true
            // Rebuild chips
            viewLifecycleOwner.lifecycleScope.launch {
                renderCityChips(viewModel.cities.value, viewModel.filter.value.cityId)
                renderTypeChips(viewModel.types.value, viewModel.filter.value.typeId)
                renderCategoryChips(viewModel.categories.value, viewModel.filter.value.categoryIds)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.cities.collect { cities ->
                        renderCityChips(cities, viewModel.filter.value.cityId)
                    }
                }
                launch {
                    viewModel.types.collect { types ->
                        renderTypeChips(types, viewModel.filter.value.typeId)
                    }
                }
                launch {
                    viewModel.categories.collect { cats ->
                        renderCategoryChips(cats, viewModel.filter.value.categoryIds)
                    }
                }
            }
        }
    }

    private fun renderCityChips(cities: List<com.zai.mamsad.api.WpTerm>, selectedId: Int?) {
        binding.chipsCity.removeAllViews()
        cities.forEach { term ->
            val chip = Chip(requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter)
            chip.text = term.name
            chip.isCheckable = true
            chip.isChecked = term.id == selectedId
            chip.setOnClickListener {
                viewModel.setCity(if (chip.isChecked) term.id else null)
            }
            binding.chipsCity.addView(chip)
        }
    }

    private fun renderTypeChips(types: List<com.zai.mamsad.api.WpTerm>, selectedId: Int?) {
        binding.chipsType.removeAllViews()
        types.forEach { term ->
            val chip = Chip(requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter)
            chip.text = term.name
            chip.isCheckable = true
            chip.isChecked = term.id == selectedId
            chip.setOnClickListener {
                viewModel.setType(if (chip.isChecked) term.id else null)
            }
            binding.chipsType.addView(chip)
        }
    }

    private fun renderCategoryChips(
        cats: List<com.zai.mamsad.api.WpTerm>,
        selectedIds: Set<Int>
    ) {
        binding.chipsCategory.removeAllViews()
        cats.forEach { term ->
            if (term.count <= 0) return@forEach  // Skip empty categories to reduce noise
            val chip = Chip(requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter)
            chip.text = "${term.name} (${term.count})"
            chip.isCheckable = true
            chip.isChecked = term.id in selectedIds
            chip.setOnClickListener {
                viewModel.toggleCategory(term.id)
            }
            binding.chipsCategory.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.zai.mamsad.ui.home

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.zai.mamsad.R
import com.zai.mamsad.api.WpTerm
import com.zai.mamsad.databinding.FragmentHomeBinding
import com.zai.mamsad.ui.CatalogViewModel
import com.zai.mamsad.ui.catalog.KindergartenAdapter
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CatalogViewModel by activityViewModels { CatalogViewModel.Factory }

    private lateinit var cityAdapter: CityAdapter
    private lateinit var recentAdapter: KindergartenAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Adapters
        cityAdapter = CityAdapter { city ->
            viewModel.setCity(city.id)
            findNavController().navigate(R.id.action_home_to_catalog)
        }
        recentAdapter = KindergartenAdapter(
            onClick = { org ->
                val args = Bundle().apply { putInt("orgId", org.id) }
                findNavController().navigate(R.id.action_home_to_detail, args)
            },
            onFavorite = { org, fav -> viewModel.setFavorite(org.id, fav) }
        )

        binding.rvCities.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCities.adapter = cityAdapter

        binding.rvRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecent.adapter = recentAdapter

        // Buttons
        binding.btnOpenCatalog.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_catalog)
        }
        binding.btnOpenFilters.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_filters)
        }
        binding.btnRetryHome.setOnClickListener { viewModel.refresh() }

        // Tools cards
        binding.cardArticles.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_articles)
        }
        binding.cardSpecialists.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_specialists)
        }
        binding.cardCompare.setOnClickListener { openCompare() }
        binding.btnCompareGo.setOnClickListener { openCompare() }

        // Swipe to refresh
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.swipeRefresh.setColorSchemeResources(R.color.mamsad_coral, R.color.mamsad_sage)

        // Observe state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeOrgs() }
                launch { observeCities() }
                launch { observeRefreshing() }
                launch { observeError() }
                launch { observeCompare() }
            }
        }
    }

    private fun openCompare() {
        findNavController().navigate(R.id.action_home_to_compare)
    }

    private suspend fun observeCompare() {
        viewModel.compareIds.collect { ids ->
            val count = ids.size
            binding.tvCompareCount.text = if (count == 0) {
                getString(R.string.compare_count_zero)
            } else {
                resources.getQuantityString(R.plurals.compare_count, count, count)
            }
        }
    }

    private suspend fun observeOrgs() {
        viewModel.filteredOrgs.collect { items ->
            // Total count
            binding.tvTotalCount.text = items.size.toString()

            // Recent (just take first 5 newest — already sorted desc by default filter)
            val recent = items.take(5)
            if (recent.isEmpty()) {
                binding.rvRecent.visibility = View.GONE
            } else {
                binding.rvRecent.visibility = View.VISIBLE
                recentAdapter.submitList(recent)
            }
        }
    }

    private suspend fun observeCities() {
        viewModel.cities.collect { cities ->
            // Quick filter chips (city)
            buildCityChips(cities)

            // City cards list — show count per city from current cached orgs
            val orgs = viewModel.filteredOrgs.value
            val withCounts = cities.map { term ->
                CityWithCount(term, orgs.count { it.cityId == term.id })
            }.sortedByDescending { it.count }
            cityAdapter.submitList(withCounts)
        }
    }

    private fun buildCityChips(cities: List<WpTerm>) {
        binding.chipsQuickCity.removeAllViews()
        cities.forEach { term ->
            val chip = Chip(requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter)
            chip.text = term.name
            chip.isCheckable = true
            chip.isChecked = viewModel.filter.value.cityId == term.id
            chip.setOnClickListener {
                viewModel.setCity(if (chip.isChecked) term.id else null)
                findNavController().navigate(R.id.action_home_to_catalog)
            }
            binding.chipsQuickCity.addView(chip)
        }
    }

    private suspend fun observeRefreshing() {
        viewModel.isRefreshing.collect { refreshing ->
            binding.swipeRefresh.isRefreshing = refreshing
            binding.progressRecent.visibility =
                if (refreshing && viewModel.hasInitialData.value.not()) View.VISIBLE else View.GONE
        }
    }

    private suspend fun observeError() {
        viewModel.refreshError.collect { err ->
            val show = err != null && viewModel.hasInitialData.value.not()
            binding.errorRecent.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvCities.adapter = null
        binding.rvRecent.adapter = null
        _binding = null
    }
}

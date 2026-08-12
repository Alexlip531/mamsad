package com.zai.mamsad.ui.map

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
import com.zai.mamsad.R
import com.zai.mamsad.data.OrgEntity
import com.zai.mamsad.databinding.FragmentMapBinding
import com.zai.mamsad.ui.CatalogViewModel
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CatalogViewModel by activityViewModels { CatalogViewModel.Factory }

    private var mapView: MapView? = null

    /**
     * Optional orgId passed from DetailFragment ("Показать на карте").
     * If set, the map zooms in on this org and shows its card on resume.
     */
    private val focusOrgId: Int? by lazy {
        arguments?.getInt("focusOrgId")?.takeIf { it != 0 }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Configure osmdroid (required for tile loading)
        Configuration.getInstance().apply {
            userAgentValue = requireContext().packageName
            osmdroidBasePath = requireContext().filesDir
            osmdroidTileCache = requireContext().cacheDir
        }

        mapView = binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(55.916, 37.80))  // Between Королёв and Мытищи
        }

        binding.btnOpenDetail.setOnClickListener {
            val id = binding.markerCard.tag as? Int ?: return@setOnClickListener
            val args = Bundle().apply { putInt("orgId", id) }
            findNavController().navigate(R.id.action_map_to_detail, args)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredOrgs.collect { items ->
                    showMarkers(items)
                }
            }
        }
    }

    private fun showMarkers(items: List<OrgEntity>) {
        val map = mapView ?: return
        map.overlays.clear()

        val withCoords = items.filter { it.lat != null && it.lng != null }
        binding.tvMarkerCount.text = resources.getQuantityString(
            R.plurals.kindergartens_count, withCoords.size, withCoords.size
        )

        if (withCoords.isEmpty()) {
            binding.stateLoading.isVisible = false
            return
        }

        for (org in withCoords) {
            val point = GeoPoint(org.lat!!, org.lng!!)
            val marker = Marker(map).apply {
                position = point
                title = org.title
                snippet = "${org.cityName} · ${org.typeName}"
                setOnMarkerClickListener { m, _ ->
                    showOrgCard(org)
                    map.controller.animateTo(point)
                    true
                }
                // Use a soft coral pin color via icon
                icon = createPinDrawable(org)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(marker)
        }

        // Zoom to fit all markers (or center on first)
        if (withCoords.size == 1) {
            map.controller.setCenter(GeoPoint(withCoords[0].lat!!, withCoords[0].lng!!))
            map.controller.setZoom(14.0)
        } else {
            val lats = withCoords.map { it.lat!! }
            val lngs = withCoords.map { it.lng!! }
            val center = GeoPoint(
                (lats.min() + lats.max()) / 2.0,
                (lngs.min() + lngs.max()) / 2.0
            )
            map.controller.setCenter(center)
        }

        // If opened from Detail ("Показать на карте"), focus the requested org
        val fid = focusOrgId
        if (fid != null) {
            val focusOrg = withCoords.firstOrNull { it.id == fid }
            if (focusOrg != null) {
                val point = GeoPoint(focusOrg.lat!!, focusOrg.lng!!)
                map.controller.setCenter(point)
                map.controller.setZoom(15.0)
                showOrgCard(focusOrg)
            }
        }

        binding.stateLoading.isVisible = false
        map.invalidate()
    }

    private fun showOrgCard(org: OrgEntity) {
        with(binding) {
            markerCard.isVisible = true
            tvMarkerTitle.text = org.title
            val parts = mutableListOf<String>()
            parts.add(org.cityName)
            if (org.priceFrom.isNotBlank()) parts.add("${org.priceFrom} ₽/мес")
            if (org.rating != null) parts.add("★ ${org.rating}")
            tvMarkerCity.text = parts.joinToString(" · ")
            markerCard.tag = org.id
        }
    }

    private fun createPinDrawable(org: OrgEntity): android.graphics.drawable.Drawable {
        // Color pin by type
        val color = when {
            org.typeName.contains("Частный", ignoreCase = true) -> getColor(R.color.mamsad_coral)
            org.typeName.contains("Муниципальн", ignoreCase = true) -> getColor(R.color.mamsad_sage)
            else -> getColor(R.color.mamsad_lilac)
        }
        // Build a simple vector pin with the chosen color
        val vd = androidx.core.content.res.ResourcesCompat.getDrawable(
            resources, R.drawable.ic_map_pin_colored, null
        ) ?: return resources.getDrawable(R.drawable.ic_nav_map, null)
        // Tint via a copy
        val m = vd.constantState?.newDrawable()?.mutate() ?: vd
        m.setColorFilter(android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN))
        return m
    }

    private fun getColor(resId: Int): Int = androidx.core.content.ContextCompat.getColor(requireContext(), resId)

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDetach()
        mapView = null
        _binding = null
    }
}

package com.zai.mamsad.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.zai.mamsad.R
import com.zai.mamsad.data.AdminOverride
import com.zai.mamsad.databinding.FragmentAdminEditBinding
import kotlinx.coroutines.launch

/**
 * Edit form for a single org's local override.
 * Empty fields are saved as null (= "use the original value").
 */
class AdminEditFragment : Fragment() {

    private var _binding: FragmentAdminEditBinding? = null
    private val binding get() = _binding!!

    private val vm: AdminViewModel by activityViewModels()
    private val args: AdminEditFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.orgs.collect { list ->
                    val org = list.firstOrNull { it.id == args.orgId } ?: return@collect
                    val ov = vm.overrides.value[args.orgId]
                    populate(org, ov)
                }
            }
        }

        binding.btnSave.setOnClickListener { saveOverride() }
        binding.btnReset.setOnClickListener { resetOverride() }
    }

    private fun populate(
        org: com.zai.mamsad.data.OrgEntity,
        ov: AdminOverride?
    ) {
        // Header (original info)
        binding.tvOriginalTitle.text = org.title
        binding.tvOriginalSubtitle.text = buildString {
            append(org.cityName.ifBlank { "—" })
            append(" · ")
            append(org.typeName.ifBlank { "—" })
        }

        // Editable fields — show override if present, else show original value as placeholder
        binding.etTitle.setText(ov?.title ?: org.title)
        binding.etExcerpt.setText(ov?.excerpt ?: org.excerpt)
        binding.etAddress.setText(ov?.address ?: org.address)
        binding.etPrice.setText(ov?.priceFrom ?: org.priceFrom)
        binding.etLat.setText(ov?.lat?.toString() ?: org.lat?.toString() ?: "")
        binding.etLng.setText(ov?.lng?.toString() ?: org.lng?.toString() ?: "")
        binding.etRating.setText(ov?.rating?.toString() ?: org.rating?.toString() ?: "")

        // Switches — avoid listener firing during initial binding
        binding.switchFeatured.setOnCheckedChangeListener(null)
        binding.switchHidden.setOnCheckedChangeListener(null)
        binding.switchFeatured.isChecked = ov?.featured ?: org.featured
        binding.switchHidden.isChecked = ov?.hidden ?: org.hidden
    }

    private fun buildOverride(): AdminOverride {
        return AdminOverride(
            orgId = args.orgId,
            title = binding.etTitle.text?.toString()?.takeIf { it.isNotBlank() },
            excerpt = binding.etExcerpt.text?.toString()?.takeIf { it.isNotBlank() },
            content = null,
            address = binding.etAddress.text?.toString()?.takeIf { it.isNotBlank() },
            priceFrom = binding.etPrice.text?.toString()?.takeIf { it.isNotBlank() },
            lat = binding.etLat.text?.toString()?.toDoubleOrNull(),
            lng = binding.etLng.text?.toString()?.toDoubleOrNull(),
            rating = binding.etRating.text?.toString()?.toFloatOrNull(),
            featured = binding.switchFeatured.isChecked,
            hidden = binding.switchHidden.isChecked
        )
    }

    private fun saveOverride() {
        val override = buildOverride()
        vm.saveOverride(override) {
            Toast.makeText(requireContext(), R.string.admin_saved, Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    private fun resetOverride() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_btn_reset)
            .setMessage(R.string.admin_reset_confirm)
            .setPositiveButton(R.string.admin_btn_reset) { _, _ ->
                vm.deleteOverride(args.orgId)
                Toast.makeText(requireContext(), R.string.admin_reset_done, Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

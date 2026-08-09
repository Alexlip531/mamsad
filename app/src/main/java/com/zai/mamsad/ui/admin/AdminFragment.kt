package com.zai.mamsad.ui.admin

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.zai.mamsad.R
import com.zai.mamsad.admin.AdminPrefs
import com.zai.mamsad.data.AdminOverride
import com.zai.mamsad.databinding.FragmentAdminBinding
import kotlinx.coroutines.launch

/**
 * Admin catalog screen — list all orgs with toggles and edit buttons.
 *
 * Entry: AboutFragment -> 7 taps on version -> password dialog -> navigate here.
 */
class AdminFragment : Fragment() {

    private var _binding: FragmentAdminBinding? = null
    private val binding get() = _binding!!

    private val vm: AdminViewModel by activityViewModels { AdminViewModel.Factory }

    private lateinit var adapter: AdminAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        adapter = AdminAdapter(
            onToggleFeatured = { vm.toggleFeatured(it) },
            onToggleHidden = { vm.toggleHidden(it) },
            onEdit = { org, _ -> openEdit(org.id) }
        )
        binding.rvAdminList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAdminList.adapter = adapter

        binding.btnChangePassword.setOnClickListener { showChangePasswordDialog() }
        binding.btnClearAll.setOnClickListener { showClearAllConfirm() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.orgsWithOverrides.collect { list ->
                    val items = list.map { AdminListItem(it.first, it.second) }
                    adapter.submitList(items)
                    binding.tvStatTotal.text = items.size.toString()
                    binding.tvStatEdited.text = items.count { it.override != null }.toString()
                    binding.tvStatHidden.text = items.count {
                        (it.override?.hidden ?: it.org.hidden)
                    }.toString()
                    binding.emptyState.visibility =
                        if (items.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvAdminList.visibility =
                        if (items.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun openEdit(orgId: Int) {
        val action = AdminFragmentDirections.actionAdminToEdit(orgId)
        findNavController().navigate(action)
    }

    private fun showChangePasswordDialog() {
        val ctx = requireContext()
        val dialogView = layoutInflater.inflate(R.layout.dialog_admin_change_password, null)
        val etCurrent = dialogView.findViewById<TextInputEditText>(R.id.et_current)
        val etNew = dialogView.findViewById<TextInputEditText>(R.id.et_new)
        val etConfirm = dialogView.findViewById<TextInputEditText>(R.id.et_confirm)

        AlertDialog.Builder(ctx)
            .setTitle(R.string.admin_btn_change_password)
            .setView(dialogView)
            .setPositiveButton(R.string.admin_btn_save) { _, _ ->
                val current = etCurrent.text?.toString().orEmpty()
                val new = etNew.text?.toString().orEmpty()
                val confirm = etConfirm.text?.toString().orEmpty()
                when {
                    !AdminPrefs.verify(ctx, current) -> {
                        Toast.makeText(ctx, R.string.admin_wrong_password, Toast.LENGTH_LONG).show()
                    }
                    new.length < 4 -> {
                        Toast.makeText(ctx, R.string.admin_password_too_short, Toast.LENGTH_LONG).show()
                    }
                    new != confirm -> {
                        Toast.makeText(ctx, R.string.admin_password_mismatch, Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        AdminPrefs.setPassword(ctx, new)
                        Toast.makeText(ctx, R.string.admin_password_changed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showClearAllConfirm() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_btn_clear_all)
            .setMessage(R.string.admin_clear_all_confirm)
            .setPositiveButton(R.string.admin_btn_clear_all) { _, _ ->
                vm.clearAllOverrides()
                Toast.makeText(requireContext(), R.string.admin_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

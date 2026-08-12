package com.zai.mamsad.ui.compare

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.zai.mamsad.R
import com.zai.mamsad.data.OrgEntity
import com.zai.mamsad.databinding.FragmentCompareBinding
import com.zai.mamsad.ui.CatalogViewModel
import kotlinx.coroutines.launch

/**
 * Compare screen — shows selected садики side by side in a table.
 *
 * Layout (programmatically built, since the number of columns varies):
 *   [Row label | Садик 1 | Садик 2 | Садик 3]
 *   [City      | ...      | ...      | ...     ]
 *   [Type      | ...      | ...      | ...     ]
 *   [Price     | ...      | ...      | ...     ]
 *   [Rating    | ...      | ...      | ...     ]
 *   [Address   | ...      | ...      | ...     ]
 *   [Tags      | ...      | ...      | ...     ]
 *   [Remove    | x        | x        | x       ]
 *
 * Because screens are narrow, max 3 садики are shown side by side. If user
 * selected more, the first 3 are shown (warned in toast).
 */
class CompareFragment : Fragment() {

    private var _binding: FragmentCompareBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CatalogViewModel by activityViewModels { CatalogViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.btnGoCatalog.setOnClickListener {
            findNavController().navigate(R.id.action_compare_to_catalog)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.compareOrgs.collect { orgs -> render(orgs) }
            }
        }
    }

    private fun render(orgs: List<OrgEntity>) {
        val container = binding.compareContainer
        container.removeAllViews()

        if (orgs.isEmpty()) {
            binding.emptyState.isVisible = true
            binding.scrollCompare.isVisible = false
            return
        }
        binding.emptyState.isVisible = false
        binding.scrollCompare.isVisible = true

        val cols = orgs.size
        val labelWeight = 1f
        val colWeight = 1.4f

        // Header row: empty label + садик names
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }
        header.addView(labelView(""))
        orgs.forEach { org -> header.addView(nameView(org.title)) }
        container.addView(header)

        // Subtitle: city + type
        val subtitle = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }
        subtitle.addView(labelView(""))
        orgs.forEach { org ->
            subtitle.addView(valueView("${org.cityName.ifBlank { "—" }} · ${org.typeName.ifBlank { "—" }}", false))
        }
        container.addView(subtitle)

        // Data rows
        addRow(container, cols, getString(R.string.compare_row_price), orgs) {
            formatPrice(it.priceFrom)
        }
        addRow(container, cols, getString(R.string.compare_row_rating), orgs) {
            if (it.rating != null && it.rating > 0)
                String.format(java.util.Locale.US, "%.1f ★", it.rating)
            else "—"
        }
        addRow(container, cols, getString(R.string.compare_row_address), orgs) {
            it.address.ifBlank { "—" }
        }
        addRow(container, cols, getString(R.string.compare_row_age), orgs) {
            it.ageGroups.ifBlank { "—" }
        }
        addRow(container, cols, getString(R.string.compare_row_categories), orgs) {
            it.categoryNames.ifBlank { "—" }
        }
        addRow(container, cols, getString(R.string.compare_row_reviews), orgs) {
            if (it.reviewCount > 0) "${it.reviewCount}" else "—"
        }

        // Remove buttons row
        val removeRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 0)
        }
        removeRow.addView(labelView(""))
        orgs.forEach { org ->
            val btn = TextView(requireContext()).apply {
                text = getString(R.string.compare_btn_remove)
                setTextColor(resources.getColor(R.color.mamsad_coral, null))
                textSize = 13f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                setPadding(8, 8, 8, 8)
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.color.transparent)
                setOnClickListener { viewModel.removeFromCompare(org.id) }
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, colWeight)
            lp.marginEnd = 8
            removeRow.addView(btn, lp)
        }
        container.addView(removeRow)
    }

    private fun addRow(
        container: LinearLayout,
        cols: Int,
        label: String,
        orgs: List<OrgEntity>,
        value: (OrgEntity) -> String
    ) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 12)
            setBackgroundResource(R.drawable.bg_card_white)
            elevation = 2f
        }
        val pad = (12 * resources.displayMetrics.density).toInt()
        row.setPadding(pad, pad, pad, pad)

        row.addView(labelView(label))
        orgs.forEach { org -> row.addView(valueView(value(org), true)) }
        container.addView(row)

        // Spacer
        val spacer = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (8 * resources.displayMetrics.density).toInt()
            )
        }
        container.addView(spacer)
    }

    private fun labelView(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(resources.getColor(R.color.mamsad_ink_mute, null))
            textSize = 12f
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = 8
            layoutParams = lp
        }
    }

    private fun valueView(text: String, emphasize: Boolean): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(resources.getColor(R.color.mamsad_ink, null))
            textSize = 13f
            if (emphasize) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f)
            lp.marginEnd = 8
            layoutParams = lp
        }
    }

    private fun nameView(text: String): TextView {
        return TextView(requireContext()).apply {
            val sb = SpannableStringBuilder(text)
            sb.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            this.text = sb
            setTextColor(resources.getColor(R.color.mamsad_coral_dark, null))
            textSize = 14f
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f)
            lp.marginEnd = 8
            layoutParams = lp
        }
    }

    private fun formatPrice(priceFrom: String): String {
        if (priceFrom.isBlank()) return "—"
        val digits = priceFrom.replace(Regex("\\D"), "")
        return if (digits.length >= 4) {
            "${digits.dropLast(3)} ${digits.takeLast(3)} ₽"
        } else if (digits.isNotEmpty()) {
            "$digits ₽"
        } else priceFrom
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

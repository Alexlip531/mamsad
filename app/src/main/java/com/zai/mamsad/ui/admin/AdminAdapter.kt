package com.zai.mamsad.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zai.mamsad.data.AdminOverride
import com.zai.mamsad.data.OrgEntity
import com.zai.mamsad.databinding.ItemAdminOrgBinding

/**
 * Adapter for the admin catalog list.
 * Each row shows: title, edited badge, city/type, featured/hidden toggles, edit button.
 */
class AdminAdapter(
    private val onToggleFeatured: (OrgEntity) -> Unit,
    private val onToggleHidden: (OrgEntity) -> Unit,
    private val onEdit: (OrgEntity, AdminOverride?) -> Unit
) : ListAdapter<AdminListItem, AdminAdapter.VH>(DIFF) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).org.id.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAdminOrgBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val b: ItemAdminOrgBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: AdminListItem) {
            val org = item.org
            val ov = item.override
            b.tvTitle.text = org.title
            b.tvSubtitle.text = buildString {
                append(org.cityName.ifBlank { "—" })
                append(" · ")
                append(org.typeName.ifBlank { "—" })
            }
            b.badgeEdited.visibility = if (ov != null) android.view.View.VISIBLE else android.view.View.GONE

            // Avoid listener firing during initial binding
            b.switchFeatured.setOnCheckedChangeListener(null)
            b.switchHidden.setOnCheckedChangeListener(null)
            b.switchFeatured.isChecked = ov?.featured ?: org.featured
            b.switchHidden.isChecked = ov?.hidden ?: org.hidden

            b.switchFeatured.setOnCheckedChangeListener { _, _ -> onToggleFeatured(org) }
            b.switchHidden.setOnCheckedChangeListener { _, _ -> onToggleHidden(org) }
            b.btnEdit.setOnClickListener { onEdit(org, ov) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AdminListItem>() {
            override fun areItemsTheSame(a: AdminListItem, b: AdminListItem) = a.org.id == b.org.id
            override fun areContentsTheSame(a: AdminListItem, b: AdminListItem) =
                a.org == b.org && a.override == b.override
        }
    }
}

data class AdminListItem(
    val org: OrgEntity,
    val override: AdminOverride?
)

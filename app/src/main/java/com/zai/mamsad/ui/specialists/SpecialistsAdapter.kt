package com.zai.mamsad.ui.specialists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zai.mamsad.databinding.ItemSpecialistBinding

/**
 * A specialist category entry. Maps to one of the kg_org_category terms
 * we want to highlight on the Specialists screen (Няни, Логопеды, etc.).
 */
data class SpecialistItem(
    val categoryId: Int,
    val name: String,
    val emoji: String
)

class SpecialistsAdapter(
    private val onClick: (SpecialistItem) -> Unit
) : ListAdapter<SpecialistItem, SpecialistsAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSpecialistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemSpecialistBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: SpecialistItem) {
            b.tvEmoji.text = item.emoji
            b.tvName.text = item.name
            b.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SpecialistItem>() {
            override fun areItemsTheSame(a: SpecialistItem, b: SpecialistItem) = a.categoryId == b.categoryId
            override fun areContentsTheSame(a: SpecialistItem, b: SpecialistItem) = a == b
        }
    }
}

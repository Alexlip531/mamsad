package com.zai.mamsad.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zai.mamsad.R
import com.zai.mamsad.data.OrgEntity
import com.zai.mamsad.databinding.ItemKindergartenCardBinding

class KindergartenAdapter(
    private val onClick: (OrgEntity) -> Unit,
    private val onFavorite: (OrgEntity, Boolean) -> Unit
) : ListAdapter<OrgEntity, KindergartenAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemKindergartenCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemKindergartenCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvTitle.text = item.title
            tvCity.text = item.cityName
            tvType.text = item.typeName
            tvExcerpt.text = item.excerpt.ifBlank { root.context.getString(R.string.catalog_loading) }

            // Favorite state
            btnFavorite.isSelected = item.isFavorite
            btnFavorite.setOnClickListener {
                val newState = !item.isFavorite
                btnFavorite.isSelected = newState
                onFavorite(item, newState)
            }

            // Card click
            root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<OrgEntity>() {
            override fun areItemsTheSame(old: OrgEntity, new: OrgEntity) = old.id == new.id
            override fun areContentsTheSame(old: OrgEntity, new: OrgEntity) = old == new
        }
    }
}

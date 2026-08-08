package com.zai.mamsad.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zai.mamsad.R
import com.zai.mamsad.api.WpTerm
import com.zai.mamsad.databinding.ItemCityCardBinding

data class CityWithCount(val term: WpTerm, val count: Int)

class CityAdapter(
    private val onClick: (WpTerm) -> Unit
) : ListAdapter<CityWithCount, CityAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemCityCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCityCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvCityName.text = item.term.name
            tvCityCount.text = root.context.resources
                .getQuantityString(R.plurals.kindergartens_count, item.count, item.count)
            root.setOnClickListener { onClick(item.term) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CityWithCount>() {
            override fun areItemsTheSame(old: CityWithCount, new: CityWithCount) =
                old.term.id == new.term.id
            override fun areContentsTheSame(old: CityWithCount, new: CityWithCount) = old == new
        }
    }
}

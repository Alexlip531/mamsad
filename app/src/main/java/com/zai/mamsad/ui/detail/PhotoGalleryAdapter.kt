package com.zai.mamsad.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.zai.mamsad.R
import com.zai.mamsad.databinding.ItemGalleryPhotoBinding

/**
 * Horizontal RecyclerView adapter for the org's photo gallery thumbnails
 * on the detail screen. Tapping a thumbnail invokes [onClick] with the
 * tapped photo's index so the host can open the fullscreen pager.
 */
class PhotoGalleryAdapter(
    private val onClick: (index: Int, all: List<String>) -> Unit
) : ListAdapter<String, PhotoGalleryAdapter.PhotoVH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoVH {
        val binding = ItemGalleryPhotoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PhotoVH(binding)
    }

    override fun onBindViewHolder(holder: PhotoVH, position: Int) {
        val url = getItem(position)
        holder.binding.imgPhoto.load(url) {
            crossfade(true)
            placeholder(R.drawable.bg_placeholder_image)
            error(R.drawable.bg_placeholder_image)
            diskCachePolicy(CachePolicy.ENABLED)
            memoryCachePolicy(CachePolicy.ENABLED)
        }
        holder.binding.root.setOnClickListener {
            onClick(position, currentList.toList())
        }
    }

    class PhotoVH(val binding: ItemGalleryPhotoBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(a: String, b: String) = a == b
            override fun areContentsTheSame(a: String, b: String) = a == b
        }
    }
}

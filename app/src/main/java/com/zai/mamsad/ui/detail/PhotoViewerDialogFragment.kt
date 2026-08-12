package com.zai.mamsad.ui.detail

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import coil.request.CachePolicy
import com.zai.mamsad.R
import com.zai.mamsad.databinding.DialogPhotoViewerBinding
import com.zai.mamsad.databinding.ItemPhotoFullBinding

/**
 * Fullscreen photo viewer opened when the user taps a thumbnail in the
 * detail screen's photo gallery. Shows a horizontal ViewPager2 with
 * pinch-to-zoom disabled (just fitCenter), a close button, and a counter.
 *
 * Pass photo URLs and the starting index via [newInstance].
 */
class PhotoViewerDialogFragment : DialogFragment() {

    private var _binding: DialogPhotoViewerBinding? = null
    private val binding get() = _binding!!

    private val photos: List<String> by lazy {
        arguments?.getStringArray(ARG_PHOTOS)?.toList() ?: emptyList()
    }
    private val startIndex: Int by lazy {
        arguments?.getInt(ARG_START_INDEX) ?: 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setBackgroundDrawableResource(android.R.color.black)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPhotoViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.pagerPhotos.adapter = FullPhotoAdapter(photos)
        binding.pagerPhotos.offscreenPageLimit = 1
        binding.pagerPhotos.setCurrentItem(startIndex.coerceIn(0, photos.lastIndex), false)

        updateCounter(startIndex.coerceIn(0, photos.lastIndex))
        binding.pagerPhotos.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateCounter(position)
        })

        binding.btnClose.setOnClickListener { dismiss() }
        // Tap on background (the pager itself) also closes
        binding.root.setOnClickListener { dismiss() }
    }

    private fun updateCounter(position: Int) {
        if (photos.isEmpty()) {
            binding.tvPhotoCounter.visibility = View.GONE
            return
        }
        binding.tvPhotoCounter.visibility = View.VISIBLE
        binding.tvPhotoCounter.text = "${position + 1} / ${photos.size}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class FullPhotoAdapter(val items: List<String>) :
        RecyclerView.Adapter<FullPhotoAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemPhotoFullBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.binding.imgPhotoFull.load(items[position]) {
                crossfade(true)
                diskCachePolicy(CachePolicy.ENABLED)
                memoryCachePolicy(CachePolicy.ENABLED)
            }
        }

        override fun getItemCount() = items.size

        inner class VH(val binding: ItemPhotoFullBinding) :
            RecyclerView.ViewHolder(binding.root)
    }

    companion object {
        private const val ARG_PHOTOS = "photos"
        private const val ARG_START_INDEX = "start_index"

        fun newInstance(photos: List<String>, startIndex: Int): PhotoViewerDialogFragment {
            return PhotoViewerDialogFragment().apply {
                arguments = Bundle().apply {
                    putStringArray(ARG_PHOTOS, photos.toTypedArray())
                    putInt(ARG_START_INDEX, startIndex)
                }
            }
        }
    }
}

package com.zai.mamsad.ui.articles

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zai.mamsad.api.WpPost
import com.zai.mamsad.databinding.ItemArticleBinding

class ArticlesAdapter(
    private val onClick: (WpPost) -> Unit
) : ListAdapter<WpPost, ArticlesAdapter.VH>(DIFF) {

    init { setHasStableIds(true) }
    override fun getItemId(position: Int): Long = getItem(position).id.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemArticleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemArticleBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(post: WpPost) {
            b.tvTitle.text = post.title.rendered.stripHtml()
            val minutes = estimateReadingMinutes(post.content.rendered)
            b.tvMeta.text = "$minutes мин чтения"
            b.root.setOnClickListener { onClick(post) }
        }

        private fun estimateReadingMinutes(html: String): Int {
            val text = html.replace(Regex("<[^>]*>"), "").trim()
            val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
            // Average reading speed: 180 words per minute
            return (words / 180).coerceAtLeast(1)
        }

        private fun String.stripHtml(): String =
            replace(Regex("<[^>]*>"), "")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&#038;", "&").trim()
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<WpPost>() {
            override fun areItemsTheSame(a: WpPost, b: WpPost) = a.id == b.id
            override fun areContentsTheSame(a: WpPost, b: WpPost) = a == b
        }
    }
}

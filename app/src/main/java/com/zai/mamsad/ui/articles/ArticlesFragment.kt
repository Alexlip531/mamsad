package com.zai.mamsad.ui.articles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.zai.mamsad.R
import com.zai.mamsad.api.WpPost
import com.zai.mamsad.databinding.FragmentArticlesBinding
import com.zai.mamsad.ui.CatalogViewModel
import kotlinx.coroutines.launch

class ArticlesFragment : Fragment() {

    private var _binding: FragmentArticlesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CatalogViewModel by activityViewModels { CatalogViewModel.Factory }

    private lateinit var adapter: ArticlesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArticlesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        adapter = ArticlesAdapter { post -> openArticle(post) }
        binding.rvArticles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvArticles.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadArticles() }
        binding.swipeRefresh.setColorSchemeResources(R.color.mamsad_coral, R.color.mamsad_sage)
        binding.btnRetry.setOnClickListener { viewModel.loadArticles() }

        viewModel.loadArticles()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeArticles() }
                launch { observeLoading() }
                launch { observeError() }
            }
        }
    }

    private suspend fun observeArticles() {
        viewModel.articles.collect { posts ->
            adapter.submitList(posts)
            val isEmpty = posts.isEmpty() && !viewModel.articlesLoading.value
            binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.rvArticles.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }

    private suspend fun observeLoading() {
        viewModel.articlesLoading.collect { loading ->
            binding.swipeRefresh.isRefreshing = false
            binding.progress.visibility =
                if (loading && viewModel.articles.value.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private suspend fun observeError() {
        viewModel.articlesError.collect { err ->
            val isEmpty = viewModel.articles.value.isEmpty()
            binding.tvEmpty.text = err ?: getString(R.string.articles_empty)
            binding.emptyState.visibility =
                if (err != null && isEmpty) View.VISIBLE else View.GONE
            binding.rvArticles.visibility =
                if (err != null && isEmpty) View.GONE else View.VISIBLE
        }
    }

    private fun openArticle(post: WpPost) {
        val args = Bundle().apply {
            putString("title", post.title.rendered.stripHtml())
            putString("content", post.content.rendered)
            putString("link", post.link)
        }
        findNavController().navigate(R.id.action_articles_to_detail, args)
    }

    private fun String.stripHtml(): String =
        replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&#038;", "&").trim()

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvArticles.adapter = null
        _binding = null
    }
}

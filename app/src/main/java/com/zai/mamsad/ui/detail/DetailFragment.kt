package com.zai.mamsad.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.zai.mamsad.R
import com.zai.mamsad.api.WpReview
import com.zai.mamsad.data.Vote
import com.zai.mamsad.databinding.FragmentDetailBinding
import com.zai.mamsad.databinding.ItemReviewBinding
import com.zai.mamsad.ui.CatalogViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CatalogViewModel by activityViewModels { CatalogViewModel.Factory }

    private val orgId: Int by lazy {
        arguments?.getInt("orgId") ?: 0
    }

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.btnOpenSite.setOnClickListener {
            val link = binding.tvSourceLink.tag as? String ?: return@setOnClickListener
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            startActivity(Intent.createChooser(intent, "Открыть в браузере"))
        }

        binding.btnFavorite.setOnClickListener {
            val isFav = binding.btnFavorite.tag as? Boolean ?: false
            viewModel.setFavorite(orgId, !isFav)
        }

        binding.btnShare.setOnClickListener { shareCurrent() }

        binding.btnCompare.setOnClickListener {
            viewModel.toggleCompare(orgId)
            val added = viewModel.isInCompare(orgId)
            val msg = if (added) getString(R.string.compare_added, viewModel.compareIds.value.size)
            else getString(R.string.compare_removed)
            android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
        }

        // Vote: tap any of 5 stars to cast a 1..5 rating; long-press to remove.
        val starViews = listOf(
            binding.star1, binding.star2, binding.star3, binding.star4, binding.star5
        )
        starViews.forEachIndexed { idx, v ->
            v.setOnClickListener {
                val rating = idx + 1
                viewModel.vote(orgId, rating)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.vote_thank_you, rating),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.btnVoteRemove.setOnClickListener {
            viewModel.removeVote(orgId)
            Toast.makeText(
                requireContext(),
                R.string.vote_removed,
                Toast.LENGTH_SHORT
            ).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeOrg() }
                launch { observeReviews() }
                launch { observeCompareState() }
                launch { observeVote(starViews) }
            }
        }
    }

    private suspend fun observeCompareState() {
        viewModel.compareIds.collect { ids ->
            val isIn = orgId in ids
            binding.btnCompare.text = getString(
                if (isIn) R.string.detail_btn_compare_added
                else R.string.detail_btn_compare
            )
        }
    }

    /**
     * Observe the user's vote for this org and reflect it in the UI:
     *   - fill stars 1..rating with ic_star_filled, leave others outlined
     *   - show "remove my vote" button only when a vote exists
     *   - show a result line "Ваш голос: N звёзд · Всего голосов: M"
     *     where M is the total number of votes across all orgs on this device.
     */
    private suspend fun observeVote(starViews: List<ImageView>) {
        viewModel.votes.collect { voteMap ->
            val vote: Vote? = voteMap[orgId]
            val userRating = vote?.rating ?: 0
            starViews.forEachIndexed { idx, v ->
                v.setImageResource(
                    if (idx < userRating) R.drawable.ic_star_filled
                    else R.drawable.ic_star_outline
                )
            }
            binding.btnVoteRemove.isVisible = vote != null
            binding.tvVoteResult.text = if (vote != null) {
                val total = voteMap.size
                getString(R.string.vote_result_voted, userRating, total)
            } else {
                val total = voteMap.size
                if (total > 0) getString(R.string.vote_result_not_voted, total)
                else getString(R.string.vote_result_empty)
            }
        }
    }

    private suspend fun observeOrg() {
        viewModel.getById(orgId).collect { org ->
            if (org == null) return@collect
            binding.toolbar.title = org.title
            binding.tvDetailTitle.text = org.title
            binding.tvDetailCity.text = org.cityName
            binding.tvDetailType.text = org.typeName
            binding.tvDetailDescription.text = org.excerpt.ifBlank {
                org.content.replace(Regex("<[^>]*>"), "").trim()
            }
            binding.tvSourceLink.text = org.link.removePrefix("https://")
            binding.tvSourceLink.tag = org.link

            // Address
            if (org.address.isNotBlank()) {
                binding.tvDetailAddress.text = org.address
                binding.tvDetailAddress.isVisible = true
                binding.tvDetailAddressEmpty.isVisible = false
            } else {
                binding.tvDetailAddress.isVisible = false
                binding.tvDetailAddressEmpty.isVisible = true
            }

            // Price
            if (org.priceFrom.isNotBlank()) {
                val price = org.priceFrom.replace(Regex("\\D"), "")
                val formatted = if (price.length >= 4) {
                    "${price.dropLast(3)} ${price.takeLast(3)} ₽/мес"
                } else {
                    "$price ₽/мес"
                }
                binding.tvDetailPrice.text = formatted
                binding.tvDetailPrice.isVisible = true
                binding.tvDetailPriceEmpty.isVisible = false
            } else {
                binding.tvDetailPrice.isVisible = false
                binding.tvDetailPriceEmpty.isVisible = true
            }

            // Rating
            if (org.rating != null && org.rating > 0) {
                binding.badgeRating.isVisible = true
                binding.tvDetailRatingValue.text = String.format(Locale.US, "%.1f", org.rating)
                val stars = buildStars(org.rating)
                binding.tvDetailRatingStars.text = stars
                val countText = if (org.reviewCount > 0) {
                    "(${org.reviewCount} ${pluralReviews(org.reviewCount)})"
                } else ""
                binding.tvDetailReviewCount.text = countText
            } else {
                binding.badgeRating.isVisible = false
            }

            // Tags
            if (org.categoryNames.isNotBlank()) {
                binding.sectionTags.isVisible = true
                binding.chipsTags.removeAllViews()
                org.categoryNames.split(",").forEach { name ->
                    val chip = Chip(requireContext())
                    chip.text = name.trim()
                    chip.isClickable = false
                    chip.setChipBackgroundColorResource(R.color.mamsad_cream_dark)
                    binding.chipsTags.addView(chip)
                }
            } else {
                binding.sectionTags.isVisible = false
            }

            // Favorite button state
            val isFav = org.isFavorite
            binding.btnFavorite.tag = isFav
            binding.btnFavorite.isSelected = isFav
            binding.btnFavorite.text = getString(
                if (isFav) R.string.detail_btn_unfavorite else R.string.detail_btn_favorite
            )

            // Trigger reviews load (only once per org)
            viewModel.loadReviews(orgId)
        }
    }

    private fun buildStars(rating: Float): String {
        val full = rating.toInt()
        val half = (rating - full) >= 0.5f
        val sb = StringBuilder()
        repeat(full) { sb.append("★") }
        if (half) sb.append("★")
        val empty = 5 - sb.length
        if (empty > 0) {
            sb.append(" ")
            repeat(empty) { sb.append("☆") }
        }
        return sb.toString().trim()
    }

    private fun pluralReviews(n: Int): String {
        return when {
            n % 10 == 1 && n % 100 != 11 -> "отзыв"
            n % 10 in 2..4 && (n % 100 < 10 || n % 100 >= 20) -> "отзыва"
            else -> "отзывов"
        }
    }

    private suspend fun observeReviews() {
        viewModel.reviews.collect { reviews ->
            if (reviews.isEmpty()) {
                if (viewModel.reviewsLoading.value) {
                    binding.reviewsProgress.isVisible = true
                    binding.reviewsEmpty.isVisible = false
                    binding.reviewsError.isVisible = false
                    binding.sectionReviews.isVisible = true
                } else if (viewModel.reviewsError.value) {
                    binding.reviewsProgress.isVisible = false
                    binding.reviewsEmpty.isVisible = false
                    binding.reviewsError.isVisible = true
                    binding.sectionReviews.isVisible = true
                } else {
                    binding.sectionReviews.isVisible = false
                }
                return@collect
            }
            binding.sectionReviews.isVisible = true
            binding.reviewsProgress.isVisible = false
            binding.reviewsError.isVisible = false
            binding.reviewsEmpty.isVisible = reviews.isEmpty()
            binding.reviewsContainer.removeAllViews()
            reviews.forEach { review ->
                val item = ItemReviewBinding.inflate(layoutInflater, binding.reviewsContainer, false)
                item.tvReviewAuthor.text = review.title.rendered.stripHtml()
                item.tvReviewText.text = review.content.rendered.stripHtml()
                item.tvReviewDate.text = try {
                    val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(review.date)
                    dateFormat.format(date)
                } catch (_: Throwable) { "" }
                item.btnReviewOpen.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(review.link))
                    startActivity(Intent.createChooser(intent, "Открыть в браузере"))
                }
                binding.reviewsContainer.addView(item.root)
            }
        }
    }

    private fun String.stripHtml(): String =
        replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#038;", "&")
            .trim()

    private fun shareCurrent() {
        val title = binding.tvDetailTitle.text.toString()
        val link = binding.tvSourceLink.tag as? String ?: "https://mamsad.ru/"
        val text = getString(R.string.detail_share_template, title, link)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.detail_btn_share)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

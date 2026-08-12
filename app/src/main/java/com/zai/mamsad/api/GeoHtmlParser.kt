package com.zai.mamsad.api

import com.squareup.moshi.JsonClass
import org.json.JSONObject

/**
 * Parsed structured-data block from an org's HTML page on mamsad.ru.
 *
 * The site renders a `<div class="kg-org-info"><table>…</table></div>` block with
 * emoji-prefixed rows for Type / Address / Price / Age / Spots / Rating. There is
 * NO JSON-LD on mamsad.ru pages, so we parse the HTML directly.
 *
 * Phone numbers, if any, are extracted from `tel:` links in the page content.
 * Photo gallery URLs are extracted from `<img src="…">` tags pointing to
 * `wp-content/uploads/` (so theme chrome images are filtered out).
 */
@JsonClass(generateAdapter = true)
data class OrgGeoInfo(
    val lat: Double?,
    val lng: Double?,
    val address: String,
    val priceFrom: String,
    val rating: Float?,
    val reviewCount: Int,
    val ageGroups: String = "",
    val phone: String? = null,
    val galleryUrls: List<String> = emptyList()
)

object GeoHtmlParser {

    // <script type="application/ld+json">...</script>  — kept for legacy sites
    // that actually emit JSON-LD. mamsad.ru currently doesn't, so this is mostly
    // a no-op for production, but it's cheap insurance.
    private val JSON_LD_REGEX = Regex(
        """<script type="application/ld\+json">(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL
    )

    // The info-table block: <div class="kg-org-info">…<table>…</table>…</div>
    // We grab the first table inside any kg-org-info div on the page.
    private val INFO_BLOCK_REGEX = Regex(
        """<div[^>]*class="[^"]*kg-org-info[^"]*"[^>]*>(.*?)</div>\s*(?:<div|<section|<aside|<footer|</main|$)""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Each row: <tr><td>📍 Адрес</td><td><b>г. Мытищи, …</b></td></tr>
    // The label is the first <td>, the value is the inner of the second <td>.
    private val ROW_REGEX = Regex(
        """<tr>\s*<td[^>]*>(.*?)</td>\s*<td[^>]*>(.*?)</td>\s*</tr>""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Phone: <a href="tel:+7XXXXXXXXXX">
    private val TEL_LINK_REGEX = Regex("""href=["']tel:([^"']+)["']""")

    // Gallery: <img src="https://mamsad.ru/wp-content/uploads/...">
    private val IMG_SRC_REGEX = Regex(
        """<img[^>]+src=["'](https?://[^"']+?/wp-content/uploads/[^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    // Also collect from srcset: "url-300x225.webp 300w, url-768x576.webp 768w, url.webp 1024w"
    private val SRCSET_REGEX = Regex(
        """(https?://[^"'\s]+?/wp-content/uploads/[^\s"']+\.(?:jpg|jpeg|png|webp|gif))""",
        RegexOption.IGNORE_CASE
    )

    // Given a list of image URLs (some with -WxH suffix like "img-300x225.webp",
    // some without like "img.webp"), group by base URL and pick the best variant
    // for each base — preferring the full-size (no -WxH suffix), or the largest
    // -WxH variant if no full-size exists.
    private fun pickBestVariants(urls: List<String>): List<String> {
        if (urls.isEmpty()) return emptyList()
        // group: base URL -> list of (width-or-Int.MAX, url)
        val groups = LinkedHashMap<String, MutableList<Pair<Int, String>>>()
        val sizeSuffix = Regex("""-(\d+)x(\d+)\.""")
        for (rawUrl in urls) {
            val m = sizeSuffix.find(rawUrl)
            val baseUrl: String
            val width: Int
            if (m != null) {
                baseUrl = rawUrl.replace(sizeSuffix, ".")
                width = m.groupValues[1].toIntOrNull() ?: 0
            } else {
                baseUrl = rawUrl
                width = Int.MAX_VALUE  // full-size is best
            }
            groups.getOrPut(baseUrl) { mutableListOf() }.add(width to rawUrl)
        }
        return groups.values.map { variants ->
            variants.maxByOrNull { it.first }?.second
        }.filterNotNull()
    }

    // Strip HTML tags + decode common entities to plain text.
    private fun String.toPlainText(): String = this
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#038;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Extract geo/address/price/rating/age/phone/gallery from an org's HTML page.
     *
     * Returns null only if we cannot find any structured block at all
     * (e.g. the page 404'd). Individual fields may be empty even when the
     * result is non-null — the site doesn't always emit all rows.
     */
    fun parse(html: String): OrgGeoInfo? {
        // ----- 1. Parse JSON-LD (legacy / future-proof) -----
        var lat: Double? = null
        var lng: Double? = null
        var reviewCount = 0
        val scripts = JSON_LD_REGEX.findAll(html).map { it.groupValues[1] }.toList()
        for (raw in scripts) {
            val json = try { JSONObject(raw) } catch (_: Throwable) { continue }
            val node = findGeoNode(json) ?: continue
            val geo = node.optJSONObject("geo")
            lat = geo?.opt("latitude")?.toString()?.toDoubleOrNull() ?: lat
            lng = geo?.opt("longitude")?.toString()?.toDoubleOrNull() ?: lng
            val ratingNode = node.optJSONObject("aggregateRating")
            reviewCount = ratingNode?.optInt("ratingCount", 0) ?: reviewCount
        }

        // ----- 2. Parse the kg-org-info HTML block -----
        var address = ""
        var priceFrom = ""
        var ratingVal: Float? = null
        var ageGroups = ""

        // We only look at the FIRST kg-org-info block — that's the org's data.
        // The block contains reviews in a separate <div class="kg-org-info" id="otzyvy">
        // that we should NOT parse for info rows.
        val firstBlockMatch = INFO_BLOCK_REGEX.findAll(html).firstOrNull()
        val scanHtml = firstBlockMatch?.let {
            // Take only up to the start of the next kg-org-info block (reviews)
            val startIdx = it.range.first
            val nextBlockIdx = html.indexOf("kg-org-info", it.range.last + 1)
            if (nextBlockIdx > 0) html.substring(startIdx, nextBlockIdx) else html.substring(startIdx)
        } ?: html

        ROW_REGEX.findAll(scanHtml).forEach { m ->
            val label = m.groupValues[1].toPlainText()
            val value = m.groupValues[2].toPlainText()
            when {
                label.contains("Адрес") -> address = value
                label.contains("Цена") -> priceFrom = value
                label.contains("Возраст") -> ageGroups = value
                label.contains("Рейтинг") -> {
                    // Rating row value is like "4.6" or "5" (no reviews count in HTML).
                    ratingVal = value.replace(",", ".").toFloatOrNull()
                }
            }
        }

        // ----- 3. Phone (scan whole page for tel: links) -----
        val phone: String? = TEL_LINK_REGEX.findAll(html)
            .map { it.groupValues[1].trim() }
            .firstOrNull { it.isNotBlank() }

        // ----- 4. Photo gallery: all <img> with src containing wp-content/uploads/ -----
        // Collect URLs from both `src` and `srcset` (srcset has larger variants).
        // Then dedupe by "base URL" (strip -WxH suffix) and prefer the largest variant.
        val rawUrls = mutableListOf<String>()
        IMG_SRC_REGEX.findAll(html).forEach { rawUrls.add(it.groupValues[1]) }
        SRCSET_REGEX.findAll(html).forEach { rawUrls.add(it.groupValues[1]) }
        val galleryUrls = pickBestVariants(rawUrls)

        // If we found NOTHING at all, return null (caller treats as "no info").
        if (lat == null && lng == null && address.isBlank() && priceFrom.isBlank()
            && ratingVal == null && ageGroups.isBlank() && phone == null
            && galleryUrls.isEmpty()
        ) {
            // Fallback: still try JSON-LD-only path (in case it exists in the future)
            if (scripts.isNotEmpty()) {
                for (raw in scripts) {
                    val json = try { JSONObject(raw) } catch (_: Throwable) { continue }
                    val node = findGeoNode(json) ?: continue
                    val geo = node.optJSONObject("geo")
                    val addr = node.optJSONObject("address")
                    val rating = node.optJSONObject("aggregateRating")
                    val nlat = geo?.opt("latitude")?.toString()?.toDoubleOrNull()
                    val nlng = geo?.opt("longitude")?.toString()?.toDoubleOrNull()
                    val addressStr = addr?.optString("streetAddress")?.takeIf { it.isNotBlank() } ?: ""
                    val price = node.optString("priceRange").trim()
                    val rVal = rating?.opt("ratingValue")?.toString()?.toFloatOrNull()
                    val nCount = rating?.optInt("ratingCount", 0) ?: 0
                    if (nlat != null && nlng != null) {
                        return OrgGeoInfo(
                            lat = nlat, lng = nlng,
                            address = addressStr, priceFrom = price,
                            rating = rVal, reviewCount = nCount,
                            ageGroups = ageGroups, phone = phone,
                            galleryUrls = galleryUrls
                        )
                    }
                }
            }
            return null
        }

        return OrgGeoInfo(
            lat = lat,
            lng = lng,
            address = address,
            priceFrom = priceFrom,
            rating = ratingVal,
            reviewCount = reviewCount,
            ageGroups = ageGroups,
            phone = phone,
            galleryUrls = galleryUrls
        )
    }

    private fun findGeoNode(obj: JSONObject): JSONObject? {
        if (obj.has("geo")) return obj
        val graph = obj.opt("graph") ?: obj.opt("@graph")
        if (graph is org.json.JSONArray) {
            for (i in 0 until graph.length()) {
                val item = graph.optJSONObject(i)
                if (item != null && item.has("geo")) return item
            }
        }
        for (key in obj.keys()) {
            val v = obj.opt(key)
            if (v is JSONObject) {
                findGeoNode(v)?.let { return it }
            } else if (v is org.json.JSONArray) {
                for (i in 0 until v.length()) {
                    val item = v.optJSONObject(i)
                    if (item != null) {
                        findGeoNode(item)?.let { return it }
                    }
                }
            }
        }
        return null
    }
}

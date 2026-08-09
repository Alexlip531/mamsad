package com.zai.mamsad.api

import com.squareup.moshi.JsonClass
import org.json.JSONObject

/**
 * Parsed JSON-LD "Preschool" / "LocalBusiness" node from an org's HTML page.
 * Contains geo, address, priceRange, aggregateRating.
 */
@JsonClass(generateAdapter = true)
data class OrgGeoInfo(
    val lat: Double?,
    val lng: Double?,
    val address: String,
    val priceFrom: String,
    val rating: Float?,
    val reviewCount: Int
)

object GeoHtmlParser {

    private val JSON_LD_REGEX = Regex("""<script type="application/ld\+json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Extract geo/address/price/rating from an org's HTML page by parsing its JSON-LD.
     */
    fun parse(html: String): OrgGeoInfo? {
        val scripts = JSON_LD_REGEX.findAll(html).map { it.groupValues[1] }.toList()
        for (raw in scripts) {
            val json = try { JSONObject(raw) } catch (_: Throwable) { continue }
            val node = findGeoNode(json) ?: continue
            val geo = node.optJSONObject("geo")
            val addr = node.optJSONObject("address")
            val rating = node.optJSONObject("aggregateRating")
            val lat = geo?.opt("latitude")?.toString()?.toDoubleOrNull()
            val lng = geo?.opt("longitude")?.toString()?.toDoubleOrNull()
            val addressStr = addr?.optString("streetAddress")?.takeIf { it.isNotBlank() } ?: ""
            val price = node.optString("priceRange").trim()
            val ratingVal = rating?.opt("ratingValue")?.toString()?.toFloatOrNull()
            val reviewCount = rating?.optInt("ratingCount", 0) ?: 0
            if (lat != null && lng != null) {
                return OrgGeoInfo(lat, lng, addressStr, price, ratingVal, reviewCount)
            }
        }
        return null
    }

    private fun findGeoNode(obj: JSONObject): JSONObject? {
        if (obj.has("geo")) return obj
        // Look in @graph array
        val graph = obj.opt("graph") ?: obj.opt("@graph")
        if (graph is org.json.JSONArray) {
            for (i in 0 until graph.length()) {
                val item = graph.optJSONObject(i)
                if (item != null && item.has("geo")) return item
            }
        }
        // Recurse into values
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

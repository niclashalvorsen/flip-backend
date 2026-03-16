package com.flip.scraper

import com.flip.models.Category
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class ScrapedProduct(
    val name: String?,
    val companyName: String?,
    val priceNok: Double?,
    val imageUrl: String?,
    val productUrl: String,
    val widthCm: Double?,
    val heightCm: Double?,
    val depthCm: Double?,
    val category: Category?,
)

object ProductScraper {

    fun scrape(url: String): ScrapedProduct {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            .timeout(10_000)
            .get()

        return tryJsonLd(doc, url)
            ?: tryOpenGraph(doc, url)
            ?: tryHeuristics(doc, url)
    }

    // MARK: - JSON-LD (Schema.org Product)

    private fun tryJsonLd(doc: Document, url: String): ScrapedProduct? {
        val scripts = doc.select("script[type=application/ld+json]")
        for (script in scripts) {
            val json = runCatching { Json.parseToJsonElement(script.data()) }.getOrNull() ?: continue
            val obj = findProductObject(json) ?: continue

            val name = obj["name"]?.jsonPrimitive?.contentOrNull
            val brand = obj["brand"]?.let {
                it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                    ?: it.jsonPrimitive?.contentOrNull
            }
            val image = extractJsonLdImage(obj)
            val price = extractJsonLdPrice(obj)
            val dimensions = extractJsonLdDimensions(obj)

            if (name != null) {
                return ScrapedProduct(
                    name = name,
                    companyName = brand,
                    priceNok = price,
                    imageUrl = image,
                    productUrl = url,
                    widthCm = dimensions?.first,
                    heightCm = dimensions?.second,
                    depthCm = dimensions?.third,
                    category = null,
                )
            }
        }
        return null
    }

    private fun findProductObject(element: JsonElement): JsonObject? {
        when (element) {
            is JsonObject -> {
                val type = element["@type"]?.jsonPrimitive?.contentOrNull
                if (type == "Product") return element
                for (value in element.values) {
                    findProductObject(value)?.let { return it }
                }
            }
            is JsonArray -> {
                for (item in element) {
                    findProductObject(item)?.let { return it }
                }
            }
            else -> {}
        }
        return null
    }

    private fun extractJsonLdImage(obj: JsonObject): String? {
        return when (val img = obj["image"]) {
            is JsonPrimitive -> img.contentOrNull
            is JsonObject -> img["url"]?.jsonPrimitive?.contentOrNull
            is JsonArray -> img.firstOrNull()?.let {
                when (it) {
                    is JsonPrimitive -> it.contentOrNull
                    is JsonObject -> it["url"]?.jsonPrimitive?.contentOrNull
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun extractJsonLdPrice(obj: JsonObject): Double? {
        val offers = obj["offers"] ?: return null
        val offerObj = when (offers) {
            is JsonObject -> offers
            is JsonArray -> offers.firstOrNull()?.jsonObject
            else -> null
        } ?: return null
        return offerObj["price"]?.jsonPrimitive?.doubleOrNull
    }

    private fun extractJsonLdDimensions(obj: JsonObject): Triple<Double, Double, Double>? {
        // Schema.org doesn't have standard dimension fields, but some sites add custom ones
        val w = obj["width"]?.jsonPrimitive?.doubleOrNull
        val h = obj["height"]?.jsonPrimitive?.doubleOrNull
        val d = obj["depth"]?.jsonPrimitive?.doubleOrNull
        return if (w != null && h != null && d != null) Triple(w, h, d) else null
    }

    // MARK: - OpenGraph fallback

    private fun tryOpenGraph(doc: Document, url: String): ScrapedProduct? {
        val title = doc.select("meta[property=og:title]").attr("content").ifBlank { null }
            ?: doc.select("meta[name=title]").attr("content").ifBlank { null }
            ?: return null

        val image = doc.select("meta[property=og:image]").attr("content").ifBlank { null }
        val price = doc.select("meta[property=product:price:amount]").attr("content").ifBlank { null }
            ?.toDoubleOrNull()
            ?: doc.select("meta[property=og:price:amount]").attr("content").ifBlank { null }
                ?.toDoubleOrNull()
        val siteName = doc.select("meta[property=og:site_name]").attr("content").ifBlank { null }

        return ScrapedProduct(
            name = title,
            companyName = siteName,
            priceNok = price,
            imageUrl = image,
            productUrl = url,
            widthCm = null,
            heightCm = null,
            depthCm = null,
            category = null,
        )
    }

    // MARK: - Heuristic fallback

    private fun tryHeuristics(doc: Document, url: String): ScrapedProduct {
        val name = doc.title().ifBlank { null }
        val image = doc.select("img[src]")
            .maxByOrNull { it.attr("src").length }
            ?.absUrl("src")
            ?.ifBlank { null }

        return ScrapedProduct(
            name = name,
            companyName = null,
            priceNok = null,
            imageUrl = image,
            productUrl = url,
            widthCm = null,
            heightCm = null,
            depthCm = null,
            category = null,
        )
    }
}

package com.flip.services

import com.flip.db.Products
import com.flip.models.Category
import com.flip.models.ProductDto
import com.flip.scraper.ProductScraper
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.util.UUID

class ImportService {

    suspend fun importFromUrl(url: String): ProductDto = dbQuery {
        val scraped = runCatching { ProductScraper.scrape(url) }.getOrElse { e ->
            throw IllegalArgumentException("Could not scrape URL: ${e.message}")
        }

        val newId = UUID.randomUUID()
        val now = OffsetDateTime.now()

        Products.insert {
            it[id] = newId
            it[companyName] = scraped.companyName ?: derivedCompanyName(url)
            it[category] = (scraped.category ?: Category.OTHER).name
            it[name] = scraped.name ?: url
            it[priceNok] = (scraped.priceNok ?: 0.0).toBigDecimal()
            it[productUrl] = scraped.productUrl
            it[widthCm] = (scraped.widthCm ?: 0.0).toBigDecimal()
            it[heightCm] = (scraped.heightCm ?: 0.0).toBigDecimal()
            it[depthCm] = (scraped.depthCm ?: 0.0).toBigDecimal()
            it[imageUrl] = scraped.imageUrl
            it[createdAt] = now
            it[updatedAt] = now
        }

        Products.selectAll()
            .where { Products.id eq newId }
            .single()
            .toDto()
    }

    private fun derivedCompanyName(url: String): String {
        return runCatching {
            java.net.URI(url).host
                .removePrefix("www.")
                .split(".").first()
                .replaceFirstChar { it.uppercase() }
        }.getOrDefault("Unknown")
    }

    private fun ResultRow.toDto() = ProductDto(
        id = this[Products.id].toString(),
        companyName = this[Products.companyName],
        category = Category.valueOf(this[Products.category]),
        name = this[Products.name],
        modelVersion = this[Products.modelVersion],
        priceNok = this[Products.priceNok].toDouble(),
        productUrl = this[Products.productUrl],
        widthCm = this[Products.widthCm].toDouble(),
        heightCm = this[Products.heightCm].toDouble(),
        depthCm = this[Products.depthCm].toDouble(),
        modelKey = this[Products.modelKey],
        hasModel = this[Products.modelKey] != null,
        imageUrl = this[Products.imageUrl],
        createdAt = this[Products.createdAt].toString(),
        updatedAt = this[Products.updatedAt].toString(),
    )

    private suspend fun <T> dbQuery(block: () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}

private typealias ResultRow = org.jetbrains.exposed.sql.ResultRow

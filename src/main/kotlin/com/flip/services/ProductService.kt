package com.flip.services

import com.flip.db.Products
import com.flip.models.*
import com.flip.storage.FileStorage
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.util.UUID

class ProductService(private val fileStorage: FileStorage) {

    suspend fun list(
        query: String?,
        category: Category?,
        page: Int,
        size: Int,
    ): ProductListResponse = dbQuery {
        val base = Products.selectAll().where {
            val parts = mutableListOf<Op<Boolean>>()
            if (!query.isNullOrBlank()) {
                val q = "%${query.lowercase()}%"
                parts += (Products.name.lowerCase() like q) or
                         (Products.companyName.lowerCase() like q)
            }
            if (category != null) {
                parts += Products.category eq category.name
            }
            parts.reduceOrNull { a, b -> a and b } ?: Op.TRUE
        }

        val total = base.count()
        val items = base
            .orderBy(Products.name to SortOrder.ASC)
            .limit(size, offset = (page * size).toLong())
            .map { it.toDto() }

        ProductListResponse(items = items, total = total, page = page, size = size)
    }

    suspend fun get(id: String): ProductDto = dbQuery {
        Products.selectAll()
            .where { Products.id eq UUID.fromString(id) }
            .singleOrNull()
            ?.toDto()
            ?: throw NoSuchElementException("Product $id not found")
    }

    suspend fun create(req: CreateProductRequest): ProductDto = dbQuery {
        val newId = UUID.randomUUID()
        val now = OffsetDateTime.now()

        Products.insert {
            it[id] = newId
            it[companyName] = req.companyName
            it[category] = req.category.name
            it[name] = req.name
            it[priceNok] = req.priceNok.toBigDecimal()
            it[productUrl] = req.productUrl
            it[widthCm] = req.widthCm.toBigDecimal()
            it[heightCm] = req.heightCm.toBigDecimal()
            it[depthCm] = req.depthCm.toBigDecimal()
            it[createdAt] = now
            it[updatedAt] = now
        }

        Products.selectAll()
            .where { Products.id eq newId }
            .single()
            .toDto()
    }

    suspend fun update(id: String, req: UpdateProductRequest): ProductDto = dbQuery {
        val uuid = UUID.fromString(id)
        val rows = Products.update({ Products.id eq uuid }) {
            req.companyName?.let { v -> it[companyName] = v }
            req.category?.let { v -> it[category] = v.name }
            req.name?.let { v -> it[name] = v }
            req.priceNok?.let { v -> it[priceNok] = v.toBigDecimal() }
            req.productUrl?.let { v -> it[productUrl] = v }
            req.widthCm?.let { v -> it[widthCm] = v.toBigDecimal() }
            req.heightCm?.let { v -> it[heightCm] = v.toBigDecimal() }
            req.depthCm?.let { v -> it[depthCm] = v.toBigDecimal() }
            it[updatedAt] = OffsetDateTime.now()
        }
        if (rows == 0) throw NoSuchElementException("Product $id not found")

        Products.selectAll()
            .where { Products.id eq uuid }
            .single()
            .toDto()
    }

    suspend fun attachModel(id: String, bytes: ByteArray): ProductDto {
        val uuid = UUID.fromString(id)
        val (key, dto) = dbQuery {
            val existing = Products.selectAll()
                .where { Products.id eq uuid }
                .singleOrNull()
                ?: throw NoSuchElementException("Product $id not found")

            val newVersion = existing[Products.modelVersion] + 1
            val key = "models/$id/v$newVersion.usdz"

            Products.update({ Products.id eq uuid }) {
                it[modelKey] = key
                it[modelVersion] = newVersion
                it[updatedAt] = OffsetDateTime.now()
            }

            key to Products.selectAll()
                .where { Products.id eq uuid }
                .single()
                .toDto()
        }
        fileStorage.store(key, bytes)
        return dto
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
        createdAt = this[Products.createdAt].toString(),
        updatedAt = this[Products.updatedAt].toString(),
    )

    private suspend fun <T> dbQuery(block: () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}

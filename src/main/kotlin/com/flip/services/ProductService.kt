package com.flip.services

import com.flip.db.Products
import com.flip.models.*
import com.flip.storage.FileStorage
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
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
        var condition: Op<Boolean> = Op.TRUE

        if (!query.isNullOrBlank()) {
            val q = "%${query.lowercase()}%"
            condition = condition and (
                (Products.name.lowerCase() like q) or
                (Products.companyName.lowerCase() like q)
            )
        }
        if (category != null) {
            condition = condition and (Products.category eq category.name)
        }

        val total = Products.selectAll().where(condition).count()
        val items = Products.selectAll()
            .where(condition)
            .orderBy(Products.name)
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
        val now = OffsetDateTime.now()
        val id = Products.insert {
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
        }[Products.id]

        Products.selectAll()
            .where { Products.id eq id }
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

    suspend fun attachModel(id: String, bytes: ByteArray): ProductDto = dbQuery {
        val uuid = UUID.fromString(id)
        val existing = Products.selectAll()
            .where { Products.id eq uuid }
            .singleOrNull()
            ?: throw NoSuchElementException("Product $id not found")

        val newVersion = existing[Products.modelVersion] + 1
        val key = "models/$id/v$newVersion.usdz"

        // Store outside the transaction (I/O), then update DB
        // We run in a coroutine so use runBlocking bridge via suspend caller
        Products.update({ Products.id eq uuid }) {
            it[modelKey] = key
            it[modelVersion] = newVersion
            it[updatedAt] = OffsetDateTime.now()
        }

        // Return the key so the route can store the bytes after commit
        key to Products.selectAll()
            .where { Products.id eq uuid }
            .single()
            .toDto()
    }.let { (key, dto) ->
        fileStorage.store(key, bytes)
        dto
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

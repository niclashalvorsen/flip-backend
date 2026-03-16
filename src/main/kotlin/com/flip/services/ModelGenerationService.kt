package com.flip.services

import com.flip.db.Products
import com.flip.models.Category
import com.flip.models.ProductDto
import com.flip.storage.FileStorage
import com.flip.threedai.ThreeDaiClient
import com.flip.threedai.ThreeDaiException
import com.flip.threedai.UsdzPackager
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.UUID

private val log = LoggerFactory.getLogger(ModelGenerationService::class.java)

class ModelGenerationService(
    private val fileStorage: FileStorage,
    apiKey: String,
) {
    private val client = ThreeDaiClient(apiKey)

    suspend fun generateModel(productId: String): ProductDto {
        val uuid = UUID.fromString(productId)

        val product = dbQuery {
            Products.selectAll()
                .where { Products.id eq uuid }
                .singleOrNull()
                ?: throw NoSuchElementException("Product $productId not found")
        }

        val imageUrl = product[Products.imageUrl]
            ?: throw IllegalArgumentException("Product has no image_url — import via POST /import first")

        log.info("[ModelGen] Starting generation for product $productId from $imageUrl")

        // Step 1: image → GLB
        val glbBytes = client.imageToGlb(imageUrl)
        log.info("[ModelGen] GLB received (${glbBytes.size} bytes)")

        // Step 2: GLB → USDC via 3DAI conversion
        // Upload GLB to temp storage so 3DAI can fetch it via URL
        val tempKey = "temp/$productId/model.glb"
        fileStorage.store(tempKey, glbBytes)
        val glbUrl = "${System.getenv("PUBLIC_BASE_URL") ?: "http://localhost:8080"}/internal/files/$tempKey"

        val usdcBytes = try {
            client.glbToUsdc(glbUrl)
        } catch (e: ThreeDaiException) {
            log.warn("[ModelGen] USDC conversion failed, packaging GLB as-is: ${e.message}")
            // Fallback: store GLB directly with .usdz extension
            // RealityKit on newer iOS can load GLB, but USDZ is preferred
            glbBytes
        } finally {
            fileStorage.delete(tempKey)
        }

        // Step 3: package USDC → USDZ
        val usdzBytes = UsdzPackager.pack(usdcBytes)
        log.info("[ModelGen] USDZ packaged (${usdzBytes.size} bytes)")

        // Step 4: store and update DB
        val currentVersion = product[Products.modelVersion]
        val newVersion = currentVersion + 1
        val storageKey = "models/$productId/v$newVersion.usdz"

        fileStorage.store(storageKey, usdzBytes)

        return dbQuery {
            Products.update({ Products.id eq uuid }) {
                it[Products.modelKey] = storageKey
                it[modelVersion] = newVersion
                it[updatedAt] = OffsetDateTime.now()
            }
            Products.selectAll()
                .where { Products.id eq uuid }
                .single()
                .toDto()
        }
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

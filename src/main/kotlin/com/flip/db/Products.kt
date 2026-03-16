package com.flip.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object Products : Table("products") {
    val id           = uuid("id").autoGenerate()
    val companyName  = varchar("company_name", 255)
    val category     = varchar("category", 50)
    val name         = varchar("name", 255)
    val modelVersion = integer("model_version").default(1)
    val priceNok     = decimal("price_nok", 10, 2)
    val productUrl   = text("product_url").nullable()
    val widthCm      = decimal("width_cm", 8, 2).default(0.toBigDecimal())
    val heightCm     = decimal("height_cm", 8, 2).default(0.toBigDecimal())
    val depthCm      = decimal("depth_cm", 8, 2).default(0.toBigDecimal())
    val modelKey     = varchar("model_key", 500).nullable()
    val createdAt    = timestampWithTimeZone("created_at")
    val updatedAt    = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

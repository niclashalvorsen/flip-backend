package com.flip.models

import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    val id: String,
    val companyName: String,
    val category: Category,
    val name: String,
    val modelVersion: Int,
    val priceNok: Double,
    val productUrl: String?,
    val widthCm: Double,
    val heightCm: Double,
    val depthCm: Double,
    val modelKey: String?,
    val hasModel: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class CreateProductRequest(
    val companyName: String,
    val category: Category,
    val name: String,
    val priceNok: Double,
    val productUrl: String? = null,
    val widthCm: Double,
    val heightCm: Double,
    val depthCm: Double,
)

@Serializable
data class UpdateProductRequest(
    val companyName: String? = null,
    val category: Category? = null,
    val name: String? = null,
    val priceNok: Double? = null,
    val productUrl: String? = null,
    val widthCm: Double? = null,
    val heightCm: Double? = null,
    val depthCm: Double? = null,
)

@Serializable
data class ProductListResponse(
    val items: List<ProductDto>,
    val total: Long,
    val page: Int,
    val size: Int,
)

@Serializable
data class ErrorResponse(val message: String)

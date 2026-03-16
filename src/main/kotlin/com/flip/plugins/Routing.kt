package com.flip.plugins

import com.flip.routes.ModelGenerationServiceKey
import com.flip.routes.importRoutes
import com.flip.routes.productRoutes
import com.flip.services.ImportService
import com.flip.services.ModelGenerationService
import com.flip.services.ProductService
import com.flip.storage.FileStorage
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.routing.*

fun Application.configureRouting(fileStorage: FileStorage) {
    install(CallLogging)

    val productService = ProductService(fileStorage)
    val importService = ImportService()

    val apiKey = System.getenv("THREEDAI_API_KEY")
    if (apiKey != null) {
        val genService = ModelGenerationService(fileStorage, apiKey)
        attributes.put(ModelGenerationServiceKey, genService)
    }

    routing {
        productRoutes(productService, fileStorage)
        importRoutes(importService)
    }
}

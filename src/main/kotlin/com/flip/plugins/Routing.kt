package com.flip.plugins

import com.flip.routes.productRoutes
import com.flip.services.ProductService
import com.flip.storage.FileStorage
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.routing.*

fun Application.configureRouting(fileStorage: FileStorage) {
    install(CallLogging)

    val productService = ProductService(fileStorage)

    routing {
        productRoutes(productService, fileStorage)
    }
}

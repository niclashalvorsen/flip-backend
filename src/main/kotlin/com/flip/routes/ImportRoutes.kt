package com.flip.routes

import com.flip.models.ImportRequest
import com.flip.services.ImportService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.importRoutes(service: ImportService) {
    post("/import") {
        val req = call.receive<ImportRequest>()
        require(req.url.startsWith("http")) { "url must be a valid HTTP/HTTPS URL" }
        val product = service.importFromUrl(req.url)
        call.respond(HttpStatusCode.Created, product)
    }
}

package com.flip.routes

import com.flip.models.Category
import com.flip.models.CreateProductRequest
import com.flip.models.ErrorResponse
import com.flip.models.UpdateProductRequest
import com.flip.services.ModelGenerationService
import com.flip.services.ProductService
import com.flip.storage.FileStorage
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*

val ModelGenerationServiceKey = AttributeKey<ModelGenerationService>("ModelGenerationService")

fun Route.productRoutes(service: ProductService, fileStorage: FileStorage) {
    route("/models") {

        // GET /models?q=chair&category=CHAIR&page=0&size=20
        get {
            val query = call.request.queryParameters["q"]
            val categoryParam = call.request.queryParameters["category"]
            val category = categoryParam?.let {
                runCatching { Category.valueOf(it.uppercase()) }.getOrNull()
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Unknown category: $it"))
                        return@get
                    }
            }
            val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
            val size = (call.request.queryParameters["size"]?.toIntOrNull() ?: 20).coerceIn(1, 100)

            call.respond(service.list(query, category, page, size))
        }

        // POST /models
        post {
            val req = call.receive<CreateProductRequest>()
            val created = service.create(req)
            call.respond(HttpStatusCode.Created, created)
        }

        route("/{id}") {

            // GET /models/{id}
            get {
                val id = call.parameters["id"]!!
                call.respond(service.get(id))
            }

            // PUT /models/{id}
            put {
                val id = call.parameters["id"]!!
                val req = call.receive<UpdateProductRequest>()
                call.respond(service.update(id, req))
            }

            // PUT /models/{id}/file  — upload .usdz
            put("/file") {
                val id = call.parameters["id"]!!
                val bytes = when (val contentType = call.request.contentType()) {
                    ContentType.Application.OctetStream -> call.receive<ByteArray>()
                    else -> {
                        // Accept multipart as well
                        val multipart = call.receiveMultipart()
                        var data: ByteArray? = null
                        multipart.forEachPart { part ->
                            if (part is PartData.FileItem) {
                                data = part.streamProvider().readBytes()
                            }
                            part.dispose()
                        }
                        data ?: run {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse("No file provided"))
                            return@put
                        }
                    }
                }

                val updated = service.attachModel(id, bytes)
                call.respond(updated)
            }

            // POST /models/{id}/generate-model  — trigger AI model generation
            post("/generate-model") {
                val id = call.parameters["id"]!!
                val genService = call.application.attributes.getOrNull(ModelGenerationServiceKey)
                    ?: run {
                        call.respond(HttpStatusCode.ServiceUnavailable,
                            ErrorResponse("THREEDAI_API_KEY not configured"))
                        return@post
                    }
                val updated = genService.generateModel(id)
                call.respond(updated)
            }

            // GET /models/{id}/file  — download .usdz
            get("/file") {
                val id = call.parameters["id"]!!
                val product = service.get(id)
                val key = product.modelKey
                    ?: run {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("No model file uploaded yet"))
                        return@get
                    }

                val bytes = fileStorage.retrieve(key)
                    ?: run {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("File not found in storage"))
                        return@get
                    }

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        "${product.name.replace(" ", "_")}.usdz"
                    ).toString()
                )
                call.respondBytes(bytes, ContentType.Application.OctetStream)
            }
        }
    }
}

package com.flip.threedai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ThreeDaiClient::class.java)

class ThreeDaiClient(private val apiKey: String) {

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    // MARK: - Image to 3D

    suspend fun imageToGlb(imageUrl: String): ByteArray {
        log.info("[3DAI] Submitting image-to-3D: $imageUrl")
        val taskId = submitImageTo3D(imageUrl)
        log.info("[3DAI] Task submitted: $taskId — polling…")
        val resultUrl = pollUntilFinished(taskId)
        log.info("[3DAI] Generation finished, downloading GLB")
        return downloadBytes(resultUrl)
    }

    private suspend fun submitImageTo3D(imageUrl: String): String {
        val response = http.post("$BASE_URL/3d-models/tencent/generate/rapid/") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(ImageTo3DRequest(imageUrl = imageUrl, enablePbr = true))
        }
        if (!response.status.isSuccess()) {
            throw ThreeDaiException("Image-to-3D submission failed (${response.status}): ${response.bodyAsText()}")
        }
        return response.body<TaskResponse>().taskId
    }

    // MARK: - GLB to USDC conversion

    suspend fun glbToUsdc(glbUrl: String): ByteArray {
        log.info("[3DAI] Submitting GLB→USDC conversion")
        val taskId = submitConversion(glbUrl, outputFormat = "usdc")
        val resultUrl = pollUntilFinished(taskId)
        log.info("[3DAI] Conversion finished, downloading USDC")
        return downloadBytes(resultUrl)
    }

    // MARK: - Polling

    private suspend fun pollUntilFinished(taskId: String): String {
        var delayMs = 3_000L
        repeat(40) { attempt ->
            delay(delayMs)
            delayMs = minOf(delayMs * 2, 30_000L)

            val status = http.get("$BASE_URL/generation-request/$taskId/status/") {
                bearerAuth(apiKey)
            }.body<StatusResponse>()

            log.info("[3DAI] Task $taskId status: ${status.status} (attempt ${attempt + 1})")

            when (status.status) {
                "FINISHED" -> {
                    return status.results?.firstOrNull()?.url
                        ?: throw ThreeDaiException("Task finished but no result URL in response")
                }
                "FAILED", "ERROR" -> throw ThreeDaiException("Task $taskId failed: ${status.error}")
            }
        }
        throw ThreeDaiException("Task $taskId timed out after polling")
    }

    private suspend fun submitConversion(modelUrl: String, outputFormat: String): String {
        val response = http.post("$BASE_URL/tools/convert/") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(ConvertRequest(modelUrl = modelUrl, outputFormat = outputFormat))
        }
        if (!response.status.isSuccess()) {
            throw ThreeDaiException("Conversion submission failed (${response.status}): ${response.bodyAsText()}")
        }
        return response.body<TaskResponse>().taskId
    }

    private suspend fun downloadBytes(url: String): ByteArray {
        val response = http.get(url)
        if (!response.status.isSuccess()) {
            throw ThreeDaiException("Download failed (${response.status}): $url")
        }
        return response.body()
    }

    // MARK: - Models

    @Serializable
    private data class ImageTo3DRequest(
        @SerialName("image_url") val imageUrl: String,
        @SerialName("enable_pbr") val enablePbr: Boolean = true,
    )

    @Serializable
    private data class ConvertRequest(
        @SerialName("model_url") val modelUrl: String,
        @SerialName("output_format") val outputFormat: String,
    )

    @Serializable
    private data class TaskResponse(
        @SerialName("task_id") val taskId: String,
    )

    @Serializable
    private data class StatusResponse(
        val status: String,
        val results: List<ResultItem>? = null,
        val error: String? = null,
    )

    @Serializable
    private data class ResultItem(
        val url: String,
        val type: String? = null,
    )

    companion object {
        private const val BASE_URL = "https://api.3daistudio.com/v1"
    }
}

class ThreeDaiException(message: String) : Exception(message)

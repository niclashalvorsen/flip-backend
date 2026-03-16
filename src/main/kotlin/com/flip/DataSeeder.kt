package com.flip

import com.flip.storage.FileStorage
import io.ktor.server.application.*
import org.slf4j.LoggerFactory
import java.io.File

private val log = LoggerFactory.getLogger("DataSeeder")

private const val NONI_ID = "c3e2a1b0-5d4f-4e3a-8b7c-6f5e4d3c2b1a"
private const val NONI_KEY = "models/$NONI_ID/v1.usdz"

/**
 * Copies seed model files into storage on first startup.
 * Looks for the USDZ at SEED_MODEL_PATH env var, or defaults to ../flip/Models/Noni_spisestol.usdz
 * relative to the working directory.
 */
suspend fun Application.seedModelFiles(fileStorage: FileStorage) {
    if (fileStorage.exists(NONI_KEY)) {
        log.info("Seed model already in storage, skipping")
        return
    }

    val modelPath = System.getenv("SEED_MODEL_PATH")
        ?: "../flip/Models/Noni_spisestol.usdz"

    val source = File(modelPath)
    if (!source.exists()) {
        log.warn("Seed model not found at '$modelPath' — upload manually via PUT /models/$NONI_ID/file")
        return
    }

    log.info("Seeding model file from ${source.absolutePath}")
    fileStorage.store(NONI_KEY, source.readBytes())
    log.info("Seed model stored at $NONI_KEY")
}

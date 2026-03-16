package com.flip

import com.flip.plugins.configureDatabase
import com.flip.plugins.configureRouting
import com.flip.plugins.configureSerialization
import com.flip.plugins.configureStatusPages
import com.flip.storage.LocalFileStorage
import io.ktor.server.application.*
import io.ktor.server.netty.*
import kotlinx.coroutines.launch

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    val storagePath = environment.config.property("storage.localPath").getString()
    val fileStorage = LocalFileStorage(storagePath)

    configureSerialization()
    configureStatusPages()
    configureDatabase()
    configureRouting(fileStorage)

    launch { seedModelFiles(fileStorage) }
}

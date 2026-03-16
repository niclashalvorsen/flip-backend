package com.flip.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalFileStorage(basePath: String) : FileStorage {
    private val root = File(basePath).also { it.mkdirs() }

    override suspend fun store(key: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val file = resolve(key)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    override suspend fun retrieve(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = resolve(key)
        if (file.exists()) file.readBytes() else null
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        resolve(key).delete()
        Unit
    }

    override fun exists(key: String): Boolean = resolve(key).exists()

    private fun resolve(key: String): File {
        val file = File(root, key).canonicalFile
        require(file.startsWith(root.canonicalFile)) { "Path traversal attempt: $key" }
        return file
    }
}

package com.flip.storage

interface FileStorage {
    suspend fun store(key: String, bytes: ByteArray)
    suspend fun retrieve(key: String): ByteArray?
    suspend fun delete(key: String)
    fun exists(key: String): Boolean
}

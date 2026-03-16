package com.flip.threedai

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packages a USDC binary + optional textures into a valid USDZ archive.
 *
 * USDZ spec: standard ZIP with STORED (no compression) entries.
 * The first entry must be the .usdc/.usda file.
 */
object UsdzPackager {

    fun pack(usdcBytes: ByteArray, textures: Map<String, ByteArray> = emptyMap()): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.setMethod(ZipOutputStream.STORED)
            addEntry(zip, "model.usdc", usdcBytes)
            for ((name, bytes) in textures) {
                addEntry(zip, "textures/$name", bytes)
            }
        }
        return out.toByteArray()
    }

    private fun addEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().also { it.update(bytes) }.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }
}

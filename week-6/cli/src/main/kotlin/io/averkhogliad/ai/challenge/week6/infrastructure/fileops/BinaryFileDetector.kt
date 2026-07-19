package io.averkhogliad.ai.challenge.week6.infrastructure.fileops

import java.nio.file.Files
import java.nio.file.Path

object BinaryFileDetector {

    private val BINARY_SIGNATURES = mapOf(
        // Image formats
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) to "PNG",
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) to "JPEG",
        byteArrayOf(0x47, 0x49, 0x46, 0x38) to "GIF",
        byteArrayOf(0x42, 0x4D) to "BMP",
        byteArrayOf(0x00, 0x00, 0x01, 0x00) to "ICO",
        // Archive formats
        byteArrayOf(0x50, 0x4B, 0x03, 0x04) to "ZIP/JAR",
        byteArrayOf(0x1F.toByte(), 0x8B.toByte()) to "GZIP",
        // PDF
        byteArrayOf(0x25, 0x50, 0x44, 0x46) to "PDF",
        // Java class
        byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()) to "Java class",
        // Executables
        byteArrayOf(0x4D, 0x5A) to "PE/EXE",
        byteArrayOf(0x7F.toByte(), 0x45, 0x4C, 0x46) to "ELF",
        // Office docs
        byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte()) to "OLE2/MS Office",
    )

    fun isBinary(filePath: Path): Boolean {
        if (!Files.isRegularFile(filePath)) return false
        if (Files.size(filePath) == 0L) return false

        return try {
            Files.newInputStream(filePath).use { stream ->
                val header = ByteArray(4)
                val bytesRead = stream.read(header)
                if (bytesRead < 4) return false

                // Check magic bytes
                for ((signature, _) in BINARY_SIGNATURES) {
                    if (matchesSignature(header, signature)) return true
                }

                // Check for null bytes in header
                for (i in 0 until bytesRead) {
                    if (header[i] == 0.toByte()) return true
                }

                // Check for null bytes in remaining first 512 bytes
                val buffer = ByteArray(512 - bytesRead)
                val bufferRead = stream.read(buffer)
                for (i in 0 until bufferRead) {
                    if (buffer[i] == 0.toByte()) return true
                }

                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun matchesSignature(header: ByteArray, signature: ByteArray): Boolean {
        if (header.size < signature.size) return false
        for (i in signature.indices) {
            if (header[i] != signature[i]) return false
        }
        return true
    }
}

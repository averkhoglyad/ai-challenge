package io.averkhogliad.ai.challenge.week4.cli.application.indexer

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Document
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.DocumentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.security.MessageDigest

/**
 * Загружает документы из файловой системы в виде [Flow]<[Document]>.
 *
 * Рекурсивно обходит директорию и создаёт [Document] для каждого файла
 * поддерживаемого типа.
 */
class DocumentLoader {

    /**
     * Загружает документы из указанного пути.
     *
     * @param path путь к файлу или директории
     * @return [Flow] документов (по одному за раз — не загружает всё в память)
     */
    fun load(path: String): Flow<Document> = flow {
        val root = File(path)
        if (!root.exists()) {
            throw IllegalArgumentException("Path does not exist: $path")
        }

        if (root.isFile) {
            val doc = fileToDocument(root)
            if (doc != null) emit(doc)
        } else {
            root.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val doc = fileToDocument(file)
                    if (doc != null) emit(doc)
                }
        }
    }

    private fun fileToDocument(file: File): Document? {
        val type = detectType(file.name) ?: return null
        val rawContent = file.readText(Charsets.UTF_8)
        val contentHash = sha256(rawContent)

        return Document(
            path = file.absolutePath,
            type = type,
            contentHash = contentHash,
            rawContent = rawContent
        )
    }

    private fun detectType(fileName: String): DocumentType? {
        return when {
            fileName.endsWith(".txt", ignoreCase = true) -> DocumentType.PLAIN_TEXT
            fileName.endsWith(".md", ignoreCase = true) -> DocumentType.MARKDOWN
            fileName.endsWith(".html", ignoreCase = true) ||
                    fileName.endsWith(".htm", ignoreCase = true) -> DocumentType.HTML

            else -> null
        }
    }

    companion object {
        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}

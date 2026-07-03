package io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.extractor

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Document
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.DocumentType
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ExtractedText
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.DocumentExtractor

/**
 * Экстрактор для plain text (.txt) файлов.
 *
 * Возвращает содержимое файла без изменений.
 */
class TextExtractor : DocumentExtractor {

    override suspend fun extract(document: Document): ExtractedText {
        return ExtractedText(
            documentPath = document.path,
            content = document.rawContent,
            metadata = mapOf("type" to DocumentType.PLAIN_TEXT.name)
        )
    }

    override fun canHandle(type: DocumentType): Boolean =
        type == DocumentType.PLAIN_TEXT
}

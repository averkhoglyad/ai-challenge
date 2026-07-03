package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Document
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.DocumentType
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.ExtractedText

/**
 * Порт для извлечения текста из документов разных форматов.
 *
 * Каждая реализация отвечает за один тип документа.
 */
interface DocumentExtractor {

    /**
     * Извлекает текст из документа.
     *
     * @param document исходный документ
     * @return извлечённый текст с метаданными
     */
    suspend fun extract(document: Document): ExtractedText

    /**
     * Проверяет, может ли экстрактор обработать данный тип документа.
     *
     * @param type тип документа
     * @return true, если экстрактор поддерживает этот тип
     */
    fun canHandle(type: DocumentType): Boolean
}

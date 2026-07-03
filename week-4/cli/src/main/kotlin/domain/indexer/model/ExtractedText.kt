package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model

/**
 * Результат извлечения текста из документа.
 *
 * Возвращается экстракторами (DocumentExtractor) и содержит
 * очищенный текст с метаданными о структуре документа.
 *
 * @property documentPath путь к исходному документу
 * @property content извлечённый текст
 * @property metadata метаданные (заголовки, секции и т.д.)
 */
data class ExtractedText(
    val documentPath: String,
    val content: String,
    val metadata: Map<String, String>
)

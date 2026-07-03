package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model

/**
 * Исходный документ для индексации.
 *
 * @property path путь к файлу (например, "./docs/api.md")
 * @property type тип документа
 * @property contentHash SHA-256 хеш содержимого файла
 * @property rawContent сырое содержимое файла
 */
data class Document(
    val path: String,
    val type: DocumentType,
    val contentHash: String,
    val rawContent: String
)

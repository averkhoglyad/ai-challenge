package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model

/**
 * Типы документов, поддерживаемые для индексации.
 *
 * Telegram JSON отложен до следующей итерации.
 */
enum class DocumentType {
    PLAIN_TEXT,
    MARKDOWN,
    HTML
}

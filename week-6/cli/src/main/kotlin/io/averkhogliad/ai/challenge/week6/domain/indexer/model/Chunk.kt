package io.averkhogliad.ai.challenge.week6.domain.indexer.model

import java.util.*

/**
 * Текстовый фрагмент (чанк) с метаданными.
 *
 * @property id уникальный идентификатор чанка
 * @property runId ссылка на IndexingRun, в рамках которого создан чанк
 * @property contentHash SHA-256 хеш (source + section + text)
 * @property source путь к исходному файлу
 * @property title имя файла
 * @property section заголовок/секция документа
 * @property text текст чанка
 * @property strategy стратегия, которой создан чанк
 * @property metadata дополнительные метаданные (например, позиция в документе)
 */
data class Chunk(
    val id: UUID,
    val runId: UUID,
    val contentHash: String,
    val source: String,
    val title: String?,
    val section: String?,
    val text: String,
    val strategy: ChunkingStrategyType,
    val metadata: Map<String, String>
)

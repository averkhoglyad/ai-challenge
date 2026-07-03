package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model

import java.time.Instant
import java.util.*

/**
 * Запись об индексации (run).
 *
 * Каждая индексация создаёт новый run, к которому привязываются все чанки.
 *
 * @property id уникальный идентификатор run
 * @property startedAt время начала индексации
 * @property finishedAt время завершения (null если ещё не завершена)
 * @property strategy использованная стратегия чанкинга
 * @property sourcePath путь к исходной директории/файлу
 * @property chunkSize размер чанка (null для STRUCTURAL)
 * @property overlap размер перекрытия (null для STRUCTURAL)
 * @property embeddingModel использованная модель эмбеддингов
 * @property status текущий статус
 * @property totalChunks общее количество созданных чанков
 * @property errorMessage сообщение об ошибке (если status == FAILED)
 * @property metadata дополнительные метаданные
 */
data class IndexingRun(
    val id: UUID,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val strategy: ChunkingStrategyType,
    val sourcePath: String,
    val chunkSize: Int?,
    val overlap: Int?,
    val embeddingModel: String,
    val status: RunStatus,
    val totalChunks: Int,
    val errorMessage: String?,
    val metadata: Map<String, String>
)

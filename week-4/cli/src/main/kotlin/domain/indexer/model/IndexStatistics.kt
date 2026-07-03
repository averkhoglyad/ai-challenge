package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model

import java.util.*

/**
 * Агрегированная статистика индекса.
 *
 * @property runId идентификатор run, к которому относится статистика
 * @property strategy стратегия чанкинга
 * @property sourcePath путь к исходной директории
 * @property totalChunks общее количество чанков
 * @property bySource распределение чанков по файлам-источникам
 * @property avgChunkSize средний размер чанка в символах
 * @property minChunkSize минимальный размер чанка в символах
 * @property maxChunkSize максимальный размер чанка в символах
 * @property indexSizeBytes размер данных индекса в байтах (приблизительно)
 */
data class IndexStatistics(
    val runId: UUID,
    val strategy: ChunkingStrategyType,
    val sourcePath: String,
    val totalChunks: Int,
    val bySource: Map<String, Int>,
    val avgChunkSize: Int,
    val minChunkSize: Int,
    val maxChunkSize: Int,
    val indexSizeBytes: Long
)

package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.IndexStatistics
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.IndexedChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.IndexingRun
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.RunStatus
import java.time.Instant
import java.util.*

/**
 * Порт для хранения и управления данными индексации.
 *
 * Обеспечивает CRUD-операции над runs, чанками и активным индексом.
 */
interface IndexRepository {

    // ──── Runs ────

    /** Создать новый run индексации */
    suspend fun createRun(run: IndexingRun)

    /** Обновить статус run */
    suspend fun updateRunStatus(
        runId: UUID,
        status: RunStatus,
        totalChunks: Int = 0,
        errorMessage: String? = null
    )

    /** Получить run по ID */
    suspend fun getRun(runId: UUID): IndexingRun?

    /** Получить все runs (от новых к старым) */
    suspend fun getAllRuns(): List<IndexingRun>

    /** Удалить конкретный run и все его чанки */
    suspend fun deleteRun(runId: UUID)

    /** Удалить все runs, созданные до указанной даты */
    suspend fun deleteRunsBefore(date: Instant)

    /** Оставить только последние N runs, остальные удалить */
    suspend fun keepLastRuns(count: Int)

    /** Удалить все runs кроме активного (если передан) */
    suspend fun deleteAllRunsExcept(activeRunId: UUID?)

    // ──── Active index ────

    /** Установить активный индекс */
    suspend fun setActiveIndex(runId: UUID)

    /** Получить ID активного индекса (null если не установлен) */
    suspend fun getActiveIndex(): UUID?

    // ──── Chunks ────

    /** Сохранить батч чанков с эмбеддингами */
    suspend fun saveBatch(chunks: List<IndexedChunk>)

    /** Получить все чанки конкретного run */
    suspend fun getChunksByRunId(runId: UUID): List<IndexedChunk>

    /** Получить агрегированную статистику по run */
    suspend fun getStatistics(runId: UUID): IndexStatistics
}

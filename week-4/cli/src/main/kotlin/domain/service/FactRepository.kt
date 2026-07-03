package io.averkhogliad.ai.challenge.week4.cli.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.model.Fact
import io.averkhogliad.ai.challenge.week4.cli.domain.model.FactId

/**
 * Репозиторий фактов долговременной памяти (Long-Term Memory).
 *
 * ## Архитектурная роль
 * - **Domain Port** — интерфейс в domain-слое, реализуется в infrastructure-слое
 * - **Hexagonal Architecture** — порт для persistence
 *
 * ## Контракт
 * - [save] — сохраняет факт (upsert)
 * - [findById] — находит факт по ID
 * - [findAll] — возвращает все факты, отсортированные по времени создания (сначала новые)
 * - [search] — полнотекстовый поиск фактов через FTS5
 * - [delete] — удаляет факт по ID
 * - [count] — возвращает количество фактов
 */
interface FactRepository {
    /** Сохраняет факт (upsert по id). */
    suspend fun save(fact: Fact): Fact

    /** Находит факт по идентификатору, или null. */
    suspend fun findById(id: FactId): Fact?

    /** Возвращает список всех фактов, отсортированных по времени создания (сначала новые). */
    suspend fun findAll(): List<Fact>

    /**
     * Полнотекстовый поиск фактов по запросу.
     *
     * @param query поисковый запрос
     * @return список фактов, соответствующих запросу
     */
    suspend fun search(query: String): List<Fact>

    /**
     * Batch-поиск фактов по нескольким ключевым словам за один запрос.
     * Устраняет N+1 проблему при последовательном вызове [search] для каждого ключевого слова.
     *
     * @param queries список поисковых запросов (ключевых слов)
     * @return уникальный список фактов, соответствующих хотя бы одному из запросов
     */
    suspend fun searchBatch(queries: List<String>): List<Fact>

    /** Удаляет факт по идентификатору. Возвращает true, если факт был удалён. */
    suspend fun delete(id: FactId): Boolean

    /** Возвращает общее количество фактов в LTM. */
    suspend fun count(): Int
}

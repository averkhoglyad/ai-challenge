package io.averkhogliad.ai.challenge.week2.domain.service

import io.averkhogliad.ai.challenge.week2.domain.model.Invariant
import io.averkhogliad.ai.challenge.week2.domain.model.InvariantId

/**
 * Репозиторий инвариантов — неизменных правил, которые агент обязан соблюдать.
 *
 * ## Архитектурная роль
 * - **Domain Port** — интерфейс в domain-слое, реализуется в infrastructure-слое
 * - **Hexagonal Architecture** — порт для persistence
 *
 * ## Контракт
 * - [save] — сохраняет инвариант (insert с автоинкрементом ID)
 * - [findById] — находит инвариант по ID
 * - [findAll] — возвращает все инварианты, отсортированные по ID
 * - [delete] — удаляет инвариант по ID. Возвращает true, если удалён
 * - [count] — возвращает количество инвариантов
 */
interface InvariantRepository : AutoCloseable {
    /** Сохраняет инвариант. Возвращает сохранённый экземпляр с присвоенным ID. */
    suspend fun save(invariant: Invariant): Invariant

    /** Находит инвариант по идентификатору, или null. */
    suspend fun findById(id: InvariantId): Invariant?

    /** Возвращает список всех инвариантов, отсортированных по ID. */
    suspend fun findAll(): List<Invariant>

    /** Удаляет инвариант по идентификатору. Возвращает true, если инвариант был удалён. */
    suspend fun delete(id: InvariantId): Boolean

    /** Возвращает общее количество инвариантов. */
    suspend fun count(): Int

    override fun close() {
        // do nothing
    }
}

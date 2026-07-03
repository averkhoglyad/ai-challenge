package io.averkhogliad.ai.challenge.week4.cli.application

import io.averkhogliad.ai.challenge.week4.cli.domain.model.Invariant
import io.averkhogliad.ai.challenge.week4.cli.domain.model.InvariantId
import io.averkhogliad.ai.challenge.week4.cli.domain.service.InvariantRepository
import java.time.Instant

/**
 * Сервис управления инвариантами агента.
 *
 * ## Архитектурная роль
 * - **Application Layer** — содержит бизнес-логику управления инвариантами
 * - Оркестрирует операции с [InvariantRepository]
 *
 * ## Операции
 * - [add] — добавляет новый инвариант
 * - [list] — возвращает список всех инвариантов
 * - [remove] — удаляет инвариант по ID
 * - [count] — возвращает количество инвариантов
 *
 * @param invariantRepository репозиторий инвариантов
 */
open class InvariantService(
    protected val invariantRepository: InvariantRepository
) {

    /**
     * Добавляет новый инвариант.
     *
     * @param rule текст правила (не может быть пустым)
     * @return сохранённый инвариант с присвоенным ID
     * @throws IllegalArgumentException если правило пустое
     */
    open suspend fun add(rule: String): Invariant {
        val tempInvariant = Invariant(
            id = InvariantId(1),  // временный ID, будет заменён при save
            rule = rule,
            createdAt = Instant.now()
        )
        return invariantRepository.save(tempInvariant)
    }

    /**
     * Возвращает список всех инвариантов, отсортированный по ID.
     */
    open suspend fun list(): List<Invariant> {
        return invariantRepository.findAll()
    }

    /**
     * Удаляет инвариант по ID.
     *
     * @param id идентификатор инварианта
     * @return true, если инвариант был удалён
     */
    open suspend fun remove(id: Int): Boolean {
        return invariantRepository.delete(InvariantId(id))
    }

    /**
     * Возвращает количество активных инвариантов.
     */
    open suspend fun count(): Int {
        return invariantRepository.count()
    }
}

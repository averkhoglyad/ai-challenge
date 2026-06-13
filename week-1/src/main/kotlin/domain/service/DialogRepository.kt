package io.averkhogliad.ai.challenge.week1.domain.service

import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.model.DialogSummary

/**
 * Port (интерфейс) для персистентного хранения диалогов.
 *
 * Определяет контракт для infrastructure-слоя (SQLite, PostgreSQL, etc.).
 * Domain-слой зависит только от этого интерфейса, реализуя принцип
 * Dependency Inversion (DIP).
 *
 * ## Архитектурная роль
 * - **Hexagonal Architecture** — port для persistence
 * - **Domain определяет контракт** — infrastructure реализует
 * - **Suspend функции** — поддержка асинхронных операций I/O
 *
 * ## Реализации
 * - [SqliteDialogRepository] — SQLite через JDBC
 * - InMemoryDialogRepository — для тестирования
 */
interface DialogRepository {
    /**
     * Сохраняет диалог в хранилище.
     *
     * Если диалог с таким ID уже существует — обновляет его.
     * Иначе — создаёт новую запись.
     *
     * @param dialog диалог для сохранения
     */
    suspend fun save(dialog: Dialog)

    /**
     * Находит диалог по идентификатору.
     *
     * @param id идентификатор диалога
     * @return найденный диалог или null, если не найден
     */
    suspend fun findById(id: DialogId): Dialog?

    /**
     * Возвращает список всех диалогов (краткое представление).
     *
     * Используется для отображения списка диалогов в UI.
     * Не содержит полную историю сообщений для экономии памяти.
     *
     * @return список кратких представлений диалогов
     */
    suspend fun findAll(): List<DialogSummary>

    /**
     * Удаляет диалог по идентификатору.
     *
     * Если диалог не существует — операция завершается успешно (идемпотентность).
     *
     * @param id идентификатор диалога для удаления
     */
    suspend fun delete(id: DialogId)
}

package io.averkhogliad.ai.challenge.week4.cli.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.model.Profile
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ProfileId

/**
 * Порт репозитория профилей (Domain Layer).
 *
 * ## Архитектурная роль
 * - **Domain Port** — интерфейс для персистентности профилей
 * - **Не зависит** от infrastructure (SQLite, in-memory)
 * - Реализуется в infrastructure layer
 */
interface ProfileRepository {
    /**
     * Сохранить профиль (создать или обновить).
     * @param profile профиль для сохранения
     * @return сохранённый профиль
     */
    suspend fun save(profile: Profile): Profile

    /**
     * Найти профиль по ID.
     * @param id идентификатор профиля
     * @return профиль или null, если не найден
     */
    suspend fun findById(id: ProfileId): Profile?

    /**
     * Найти профиль по названию.
     * @param name название профиля
     * @return профиль или null, если не найден
     */
    suspend fun findByName(name: String): Profile?

    /**
     * Получить все профили.
     * @return список всех профилей
     */
    suspend fun findAll(): List<Profile>

    /**
     * Получить активный профиль.
     * @return активный профиль или null, если нет активного
     */
    suspend fun findActive(): Profile?

    /**
     * Удалить профиль по ID.
     * @param id идентификатор профиля
     */
    suspend fun delete(id: ProfileId)

    /**
     * Проверить существование профиля с указанным названием.
     * @param name название профиля
     * @return true, если профиль с таким названием существует
     */
    suspend fun existsByName(name: String): Boolean

    /**
     * Сбросить статус активного профиля.
     * Вызывается при удалении активного профиля.
     */
    suspend fun clearActive()
}

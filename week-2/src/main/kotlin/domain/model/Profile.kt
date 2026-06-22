package io.averkhogliad.ai.challenge.week2.domain.model

import java.time.Instant

/**
 * Доменная модель профиля пользователя.
 *
 * ## Архитектурная роль
 * - **Domain Model** — сущность предметной области
 * - **Immutable** — все изменения возвращают новый экземпляр
 *
 * ## Свойства
 * - [id] — уникальный идентификатор профиля
 * - [name] — название профиля (не может быть пустым, должно быть уникальным)
 * - [description] — описание профиля (стиль/характер)
 * - [instructions] — инструкции для LLM
 * - [isActive] — флаг активного профиля
 * - [createdAt] — время создания
 * - [updatedAt] — время последнего обновления
 *
 * ## Бизнес-логика
 * - [activate()] — активирует профиль
 * - [deactivate()] — деактивирует профиль
 */
data class Profile(
    val id: ProfileId,
    val name: String,
    val description: String = "",
    val instructions: String = "",
    val isActive: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(name.isNotBlank()) { "Profile name cannot be blank" }
        // Валидация наличия содержимого (description/instructions) происходит в ProfileService на уровне приложения
    }

    /**
     * Активирует профиль.
     * @return новая копия профиля с isActive = true
     */
    fun activate(): Profile = copy(isActive = true, updatedAt = Instant.now())

    /**
     * Деактивирует профиль.
     * @return новая копия профиля с isActive = false
     */
    fun deactivate(): Profile = copy(isActive = false, updatedAt = Instant.now())

    /**
     * Обновляет описание профиля.
     * @param newDescription новое описание
     * @return новая копия профиля с обновлённым описанием
     */
    fun updateDescription(newDescription: String): Profile {
        return copy(description = newDescription, updatedAt = Instant.now())
    }

    /**
     * Обновляет инструкции профиля.
     * @param newInstructions новые инструкции
     * @return новая копия профиля с обновлёнными инструкциями
     */
    fun updateInstructions(newInstructions: String): Profile {
        return copy(instructions = newInstructions, updatedAt = Instant.now())
    }

    /**
     * Обновляет название профиля.
     * @param newName новое название
     * @return новая копия профиля с обновлённым названием
     */
    fun updateName(newName: String): Profile {
        require(newName.isNotBlank()) { "Profile name cannot be blank" }
        return copy(name = newName, updatedAt = Instant.now())
    }
}
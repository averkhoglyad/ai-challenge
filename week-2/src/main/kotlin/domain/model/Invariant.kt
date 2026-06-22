package io.averkhogliad.ai.challenge.week2.domain.model

import java.time.Instant

/**
 * Доменная модель инварианта — неизменного правила, которое агент обязан соблюдать.
 *
 * ## Архитектурная роль
 * - **Domain Model** — сущность предметной области
 * - **Immutable** — все поля val, data class
 *
 * ## Свойства
 * - [id] — уникальный идентификатор инварианта (автоинкремент)
 * - [rule] — текст правила (не может быть пустым)
 * - [createdAt] — время создания
 *
 * ## Инварианты
 * - [rule] не может быть пустым или состоять только из пробелов
 */
data class Invariant(
    val id: InvariantId,
    val rule: String,
    val createdAt: Instant
) {
    init {
        require(rule.isNotBlank()) { "Текст инварианта не может быть пустым" }
    }
}

package io.averkhogliad.ai.challenge.week2.domain.model

import java.time.Instant

/**
 * Value object — уникальный идентификатор факта в LTM.
 *
 * ## Архитектурная роль
 * - **Value Object** — иммутабельный идентификатор
 * - **Domain Model** — часть доменной модели LTM
 */
@JvmInline
value class FactId(val value: String) {
    init {
        require(value.isNotBlank()) { "ID факта не может быть пустым" }
    }
}

/**
 * Модель факта долговременной памяти (Long-Term Memory).
 *
 * ## Архитектурная роль
 * - **Domain Model** — представляет единицу знаний в LTM
 * - **Immutable** — все поля val, data class
 *
 * ## Свойства
 * - [id] — уникальный идентификатор факта
 * - [content] — текстовое содержимое факта
 * - [createdAt] — время создания факта
 *
 * ## Инварианты
 * - [content] не может быть пустым или состоять только из пробелов
 */
data class Fact(
    val id: FactId,
    val content: String,
    val createdAt: Instant
) {
    init {
        require(content.isNotBlank()) { "Содержимое факта не может быть пустым" }
    }
}

package io.averkhogliad.ai.challenge.week2.domain.model

/**
 * Value object — уникальный идентификатор инварианта.
 *
 * ## Архитектурная роль
 * - **Value Object** — иммутабельный идентификатор
 * - **Domain Model** — часть доменной модели инвариантов
 */
@JvmInline
value class InvariantId(val value: Int) {
    init {
        require(value > 0) { "ID инварианта должен быть положительным числом" }
    }
}

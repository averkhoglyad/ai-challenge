package io.averkhogliad.ai.challenge.week3.cli.domain.model

/**
 * Value object для идентификатора профиля.
 *
 * ## Архитектурная роль
 * - **Domain Value Object** — неизменяемый идентификатор
 * - Используется для типобезопасной идентификации профилей
 */
@JvmInline
value class ProfileId(val value: String) {
    init {
        require(value.isNotBlank()) { "Profile ID cannot be blank" }
    }
}

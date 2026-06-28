package io.averkhogliad.ai.challenge.week3.cli.domain.model

import java.util.*

/**
 * Value object для уникального идентификатора сессии диалога.
 *
 * Использует [@JvmInline](https://kotlinlang.org/docs/inline-classes.html)
 * для zero-overhead абстракции. Гарантирует типобезопасность при работе
 * с идентификаторами сессий.
 *
 * @property value строковое представление идентификатора (UUID)
 */
@JvmInline
value class SessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "SessionId cannot be blank" }
    }

    override fun toString(): String = value

    companion object {
        /**
         * Создаёт новый уникальный идентификатор сессии.
         *
         * @return новый [SessionId] с случайно сгенерированным UUID
         */
        fun generate(): SessionId = SessionId(UUID.randomUUID().toString())
    }
}

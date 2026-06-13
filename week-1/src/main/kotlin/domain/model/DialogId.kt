package io.averkhogliad.ai.challenge.week1.domain.model

/**
 * Value object для уникального идентификатора диалога.
 *
 * Использует [@JvmInline](https://kotlinlang.org/docs/inline-classes.html)
 * для zero-overhead абстракции. Гарантирует типобезопасность при работе
 * с идентификаторами диалогов.
 *
 * @property value строковое представление идентификатора (UUID)
 */
@JvmInline
value class DialogId(val value: String) {
    init {
        require(value.isNotBlank()) { "DialogId cannot be blank" }
    }

    override fun toString(): String = value
}

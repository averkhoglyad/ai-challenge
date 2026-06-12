package io.averkhogliad.ai.challenge.week1.domain

/**
 * Value object для идентификатора модели.
 *
 * Гарантирует, что идентификатор модели не пустой и не состоит из одних пробелов.
 * Использует [@JvmInline](https://kotlinlang.org/docs/inline-classes.html)
 * для zero-overhead абстракции.
 */
@JvmInline
value class ModelId(val value: String) {
    init {
        require(value.isNotBlank()) { "ModelId cannot be blank" }
    }
}

package io.averkhogliad.ai.challenge.week4.cli.domain

/**
 * Value object для промпта (текстового запроса к модели).
 *
 * Гарантирует, что промпт не пустой и не состоит из одних пробелов.
 * Использует [@JvmInline](https://kotlinlang.org/docs/inline-classes.html)
 * для zero-overhead абстракции.
 */
@JvmInline
value class Prompt(val value: String) {
    init {
        require(value.isNotBlank()) { "Prompt cannot be blank" }
    }
}

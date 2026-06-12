package io.averkhogliad.ai.challenge.week1.domain

/**
 * Value object для идентификатора задачи.
 *
 * Гарантирует, что идентификатор всегда положительный, а не просто произвольный Int.
 * Использует [@JvmInline](https://kotlinlang.org/docs/inline-classes.html)
 * для стирания типа в рантайме (zero-overhead abstraction).
 */
@JvmInline
value class TaskId(val value: Int) {
    init {
        require(value > 0) { "TaskId must be positive, got: $value" }
    }
}

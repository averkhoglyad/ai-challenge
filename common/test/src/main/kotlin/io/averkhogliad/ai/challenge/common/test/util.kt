package io.averkhogliad.ai.challenge.common.test

import java.sql.Timestamp
import java.time.Instant

/**
 * Вспомогательные функции для тестового кода.
 */

/**
 * Преобразует значение JDBC-колонки времени в [Instant].
 *
 * Полезно в интеграционных тестах SQLite/H2, где драйвер может вернуть как
 * [Instant], так и [Timestamp].
 */
fun Any.asInstantColumn(): Instant {
    return when (this) {
        is Instant -> this
        is Timestamp -> this.toInstant()
        else -> throw IllegalStateException("Cannot convert ${this::class.simpleName} to Instant")
    }
}

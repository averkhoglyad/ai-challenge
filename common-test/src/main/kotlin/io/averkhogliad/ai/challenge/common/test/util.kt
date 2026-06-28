package io.averkhogliad.ai.challenge.common.test

import java.sql.Timestamp
import java.time.Instant

/**
 * Utility functions for test data conversion.
 */

/**
 * Converts a database column value to Instant.
 * Handles both Instant and Timestamp types returned by JDBC.
 * 
 * Usage:
 * ```kotlin
 * val timestamp: Instant = resultSet.getObject("created_at").asInstantColumn()
 * ```
 */
fun Any.asInstantColumn(): Instant {
    return when (this) {
        is Instant -> this
        is Timestamp -> this.toInstant()
        else -> throw IllegalStateException("Cannot convert ${this::class.simpleName} to Instant")
    }
}

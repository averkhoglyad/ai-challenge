package io.averkhogliad.ai.challenge.week3.events.core.persistence

import org.springframework.data.jdbc.core.dialect.DialectResolver.JdbcDialectProvider
import org.springframework.data.relational.core.dialect.Dialect
import org.springframework.jdbc.core.JdbcOperations
import java.sql.Connection
import java.util.*

class SqliteDialectProvider : JdbcDialectProvider {

    override fun getDialect(operations: JdbcOperations): Optional<Dialect> {
        return Optional.ofNullable(
            operations.execute { connection: Connection ->
                val productName = connection.metaData.databaseProductName.lowercase(Locale.ENGLISH)
                if (productName.contains("sqlite")) {
                    SqliteDialect()
                } else {
                    null
                }
            }
        )
    }
}
package io.averkhogliad.ai.challenge.week6.infrastructure.db

import org.jetbrains.exposed.v1.jdbc.Database
import org.sqlite.SQLiteConfig
import java.nio.file.Path

object DatabaseFactory {

    fun connect(dbPath: Path): Database {
        val url = "jdbc:sqlite:$dbPath"
        val sqliteConfig = SQLiteConfig().apply {
            enforceForeignKeys(true)
        }
        return Database.connect(
            url = url,
            driver = "org.sqlite.JDBC",
            setupConnection = { connection -> sqliteConfig.apply(connection) },
        )
    }
}

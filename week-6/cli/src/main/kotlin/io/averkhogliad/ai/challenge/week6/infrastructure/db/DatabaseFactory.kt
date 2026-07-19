package io.averkhogliad.ai.challenge.week6.infrastructure.db

import org.jetbrains.exposed.v1.jdbc.Database
import java.nio.file.Path

object DatabaseFactory {

    fun connect(dbPath: Path): Database {
        val url = "jdbc:sqlite:$dbPath"
        val db = Database.connect(
            url = url,
            driver = "org.sqlite.JDBC",
        )
        org.jetbrains.exposed.v1.jdbc.transactions.transaction(db) {
            exec("PRAGMA journal_mode=WAL")
            exec("PRAGMA foreign_keys = ON")
        }
        return db
    }
}

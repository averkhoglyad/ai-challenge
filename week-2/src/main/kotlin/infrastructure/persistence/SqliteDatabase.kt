package io.averkhogliad.ai.challenge.week2.infrastructure.persistence

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Единый владелец SQLite JDBC-соединения приложения.
 *
 * Репозитории используют [connection], но не закрывают его: lifecycle базы данных
 * централизован в composition root и [close].
 */
class SqliteDatabase(
    private val dbPath: String = defaultDbPath()
) : AutoCloseable {

    private val connectionDelegate = lazy {
        ensureDatabaseDirectoryExists()
        DriverManager.getConnection("jdbc:sqlite:$dbPath").apply {
            createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode=WAL")
                stmt.execute("PRAGMA synchronous=NORMAL")
            }
        }
    }

    val connection: Connection by connectionDelegate

    override fun close() {
        if (connectionDelegate.isInitialized() && !connection.isClosed) {
            connection.close()
        }
    }


    private fun ensureDatabaseDirectoryExists() {
        val parentDir = File(dbPath).parentFile ?: return
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
    }

    companion object {
        /**
         * Возвращает путь к БД по умолчанию: ~/.ai-challenge/week2.db
         */
        fun defaultDbPath(): String {
            val home = System.getProperty("user.home")
            return "$home/.ai-challenge/week2.db"
        }
    }
}

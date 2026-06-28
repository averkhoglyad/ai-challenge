package io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence

import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
import io.averkhogliad.ai.challenge.week3.cli.domain.service.DialogSessionRepository
import java.sql.ResultSet
import java.time.Instant

/**
 * Реализация [DialogSessionRepository] на SQLite через JDBC.
 *
 * ## Архитектурная роль
 * - **Infrastructure Layer** — реализует port из domain-слоя
 * - **Hexagonal Architecture** — адаптер для persistence
 *
 * ## Схема БД
 * - Таблица `dialog_sessions`: id, level, task_id, is_active, created_at, updated_at
 * - Таблица `messages`: id, session_id, role, content, timestamp
 *
 * ## Особенности
 * - Автоматическое создание схемы при инициализации
 * - Файл БД по умолчанию: ~/.ai-challenge/week2.db
 * - Поддержка транзакций для атомарности операций (save, delete)
 * - Автоматическое создание директории, если она не существует
 *
 * @param database единый владелец SQLite JDBC-соединения
 */
class SqliteDialogSessionRepository(
    private val database: SqliteDatabase
) : DialogSessionRepository {

    private val connection get() = database.connection


    init {
        initializeSchema()
    }

    /**
     * Создаёт таблицы БД для сессий и сообщений, если они не существуют.
     */
    private fun initializeSchema() {
        connection.createStatement().use { stmt ->
            // Таблица сессий диалога
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS dialog_sessions (
                    id TEXT PRIMARY KEY,
                    level TEXT NOT NULL CHECK(level IN ('TASK_LIST', 'TASK_DETAIL')),
                    task_id TEXT,
                    is_active INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """.trimIndent()
            )

            // Таблица сообщений
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS messages (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    role TEXT NOT NULL CHECK(role IN ('SYSTEM', 'USER', 'ASSISTANT')),
                    content TEXT NOT NULL,
                    timestamp TEXT NOT NULL,
                    FOREIGN KEY (session_id) REFERENCES dialog_sessions(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // Индекс для быстрого поиска сообщений по session_id
            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_messages_session_id 
                ON messages(session_id)
                """.trimIndent()
            )

            // Индекс для быстрого поиска сессии по task_id
            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_dialog_sessions_task_id 
                ON dialog_sessions(task_id)
                """.trimIndent()
            )
        }
    }

    override fun save(session: DialogSession): DialogSession {
        connection.autoCommit = false
        try {
            // Upsert сессии
            val sessionSql = """
                INSERT OR REPLACE INTO dialog_sessions 
                (id, level, task_id, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

            connection.prepareStatement(sessionSql).use { stmt ->
                stmt.setString(1, session.id.value)
                stmt.setString(2, session.level.name)
                stmt.setString(3, session.taskId?.value)
                stmt.setInt(4, if (session.isActive()) 1 else 0)
                stmt.setString(5, session.createdAt.toString())
                stmt.setString(6, session.updatedAt.toString())
                stmt.executeUpdate()
            }

            // Удаляем старые сообщения для этой сессии (для upsert логики)
            val deleteMessagesSql = "DELETE FROM messages WHERE session_id = ?"
            connection.prepareStatement(deleteMessagesSql).use { stmt ->
                stmt.setString(1, session.id.value)
                stmt.executeUpdate()
            }

            // Вставляем все сообщения сессии
            if (session.messages.isNotEmpty()) {
                val messageSql = """
                    INSERT INTO messages (id, session_id, role, content, timestamp)
                    VALUES (?, ?, ?, ?, ?)
                """.trimIndent()

                connection.prepareStatement(messageSql).use { stmt ->
                    for (message in session.messages) {
                        stmt.setString(1, message.id)
                        stmt.setString(2, message.sessionId.value)
                        stmt.setString(3, message.role.name)
                        stmt.setString(4, message.content)
                        stmt.setString(5, message.timestamp.toString())
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
            }

            connection.commit()
            return session
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override fun findById(id: SessionId): DialogSession? {
        // Загружаем сессию
        val sessionSql = """
            SELECT id, level, task_id, is_active, created_at, updated_at 
            FROM dialog_sessions 
            WHERE id = ?
        """.trimIndent()

        val session = connection.prepareStatement(sessionSql).use { stmt ->
            stmt.setString(1, id.value)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    mapRowToDialogSession(rs)
                } else {
                    null
                }
            }
        } ?: return null

        // Загружаем сообщения для сессии
        val messages = loadMessagesForSession(id)
        return session.copy(messages = messages)
    }

    override fun findByTaskId(taskId: TaskId): DialogSession? {
        val sessionSql = """
            SELECT id, level, task_id, is_active, created_at, updated_at 
            FROM dialog_sessions 
            WHERE task_id = ?
            ORDER BY updated_at DESC
            LIMIT 1
        """.trimIndent()

        val session = connection.prepareStatement(sessionSql).use { stmt ->
            stmt.setString(1, taskId.value)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    mapRowToDialogSession(rs)
                } else {
                    null
                }
            }
        } ?: return null

        // Загружаем сообщения для сессии
        val messages = loadMessagesForSession(session.id)
        return session.copy(messages = messages)
    }

    override fun findActiveSession(): DialogSession? {
        val sessionSql = """
            SELECT id, level, task_id, is_active, created_at, updated_at 
            FROM dialog_sessions 
            WHERE is_active = 1
            ORDER BY updated_at DESC
            LIMIT 1
        """.trimIndent()

        val session = connection.createStatement().use { stmt ->
            stmt.executeQuery(sessionSql).use { rs ->
                if (rs.next()) {
                    mapRowToDialogSession(rs)
                } else {
                    null
                }
            }
        } ?: return null

        // Загружаем сообщения для сессии
        val messages = loadMessagesForSession(session.id)
        return session.copy(messages = messages)
    }

    override fun delete(id: SessionId) {
        connection.autoCommit = false
        try {
            // Сначала удаляем сообщения (из-за FOREIGN KEY)
            val deleteMessagesSql = "DELETE FROM messages WHERE session_id = ?"
            connection.prepareStatement(deleteMessagesSql).use { stmt ->
                stmt.setString(1, id.value)
                stmt.executeUpdate()
            }

            // Затем удаляем сессию
            val deleteSessionSql = "DELETE FROM dialog_sessions WHERE id = ?"
            connection.prepareStatement(deleteSessionSql).use { stmt ->
                stmt.setString(1, id.value)
                stmt.executeUpdate()
            }

            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    /**
     * Загружает все сообщения для указанной сессии, отсортированные по времени.
     *
     * @param sessionId идентификатор сессии
     * @return список сообщений, отсортированных по timestamp
     */
    private fun loadMessagesForSession(sessionId: SessionId): List<Message> {
        val sql = """
            SELECT id, session_id, role, content, timestamp 
            FROM messages 
            WHERE session_id = ?
            ORDER BY timestamp ASC
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, sessionId.value)
            stmt.executeQuery().use { rs ->
                val messages = mutableListOf<Message>()
                while (rs.next()) {
                    messages.add(mapRowToMessage(rs))
                }
                messages
            }
        }
    }

    /**
     * Маппит строку ResultSet в доменную модель DialogSession.
     */
    private fun mapRowToDialogSession(rs: ResultSet): DialogSession {
        return DialogSession(
            id = SessionId(rs.getString("id")),
            level = SessionLevel.valueOf(rs.getString("level")),
            taskId = rs.getString("task_id")?.let { TaskId(it) },
            messages = emptyList(), // Сообщения загружаются отдельно
            createdAt = Instant.parse(rs.getString("created_at")),
            updatedAt = Instant.parse(rs.getString("updated_at"))
        )
    }

    /**
     * Маппит строку ResultSet в доменную модель Message.
     */
    private fun mapRowToMessage(rs: ResultSet): Message {
        return Message(
            id = rs.getString("id"),
            sessionId = SessionId(rs.getString("session_id")),
            role = MessageRole.valueOf(rs.getString("role")),
            content = rs.getString("content"),
            timestamp = Instant.parse(rs.getString("timestamp"))
        )
    }


}

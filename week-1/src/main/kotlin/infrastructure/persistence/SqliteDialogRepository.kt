package io.averkhogliad.ai.challenge.week1.infrastructure.persistence

import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.model.DialogSummary
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import io.averkhogliad.ai.challenge.week1.domain.service.ChatRole
import io.averkhogliad.ai.challenge.week1.domain.service.DialogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

/**
 * Реализация [DialogRepository] на SQLite через JDBC.
 *
 * ## Архитектурная роль
 * - **Infrastructure Layer** — реализует port из domain-слоя
 * - **Hexagonal Architecture** — адаптер для persistence
 * - **Suspend функции** — блокирующие JDBC операции выполняются в IO dispatcher
 *
 * ## Схема БД
 * - Таблица `dialogs`: id, title, created_at, updated_at
 * - Таблица `messages`: id, dialog_id, role, content, created_at
 *
 * ## Особенности
 * - Автоматическое создание схемы при инициализации
 * - Файл БД создаётся в директории ~/ai-challenge/dialogs.db
 * - Поддержка транзакций для атомарности операций
 * - Автоматическое создание директории, если она не существует
 *
 * @param dbPath путь к файлу базы данных (по умолчанию ~/ai-challenge/dialogs.db)
 */
class SqliteDialogRepository(
    private val dbPath: String = defaultDbPath()
) : DialogRepository {

    companion object {
        /**
         * Возвращает путь к БД по умолчанию: ~/ai-challenge/dialogs.db
         */
        fun defaultDbPath(): String {
            val home = System.getProperty("user.home")
            return "$home/.ai-challenge/dialogs.db"
        }
    }

    private val connection: Connection by lazy {
        // Создаём директорию, если она не существует
        val parentDir = java.io.File(dbPath).parentFile
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }

        DriverManager.getConnection("jdbc:sqlite:$dbPath").apply {
            // Включаем режим WAL для лучшей производительности
            createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode=WAL")
                stmt.execute("PRAGMA synchronous=NORMAL")
            }
        }
    }

    init {
        initializeSchema()
    }

    /**
     * Создаёт таблицы БД, если они не существуют.
     */
    private fun initializeSchema() {
        connection.createStatement().use { stmt ->
            // Таблица диалогов
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS dialogs (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
            """.trimIndent()
            )

            // Таблица сообщений
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    dialog_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (dialog_id) REFERENCES dialogs(id) ON DELETE CASCADE
                )
            """.trimIndent()
            )

            // Индекс для быстрого поиска сообщений по dialog_id
            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_messages_dialog_id 
                ON messages(dialog_id)
            """.trimIndent()
            )
        }
    }

    override suspend fun save(dialog: Dialog): Unit = withContext(Dispatchers.IO) {
        connection.transaction {
            // Сохраняем или обновляем диалог
            val dialogSql = """
                INSERT OR REPLACE INTO dialogs (id, title, created_at, updated_at)
                VALUES (?, ?, ?, ?)
            """.trimIndent()

            prepareStatement(dialogSql).use { stmt ->
                stmt.setString(1, dialog.id.value)
                stmt.setString(2, dialog.title)
                stmt.setString(3, dialog.createdAt.toString())
                stmt.setString(4, dialog.updatedAt.toString())
                stmt.executeUpdate()
            }

            // Удаляем старые сообщения для этого диалога
            val deleteMessagesSql = "DELETE FROM messages WHERE dialog_id = ?"
            prepareStatement(deleteMessagesSql).use { stmt ->
                stmt.setString(1, dialog.id.value)
                stmt.executeUpdate()
            }

            // Вставляем все сообщения заново
            val messageSql = """
                INSERT INTO messages (dialog_id, role, content, created_at)
                VALUES (?, ?, ?, ?)
            """.trimIndent()

            prepareStatement(messageSql).use { stmt ->
                for (message in dialog.messages) {
                    stmt.setString(1, dialog.id.value)
                    stmt.setString(2, message.role.roleName)
                    stmt.setString(3, message.content)
                    stmt.setString(4, message.createdAt.toString())
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override suspend fun findById(id: DialogId): Dialog? = withContext(Dispatchers.IO) {
        // Загружаем диалог
        val dialogSql = "SELECT id, title, created_at, updated_at FROM dialogs WHERE id = ?"
        val dialog = connection.prepareStatement(dialogSql).use { stmt ->
            stmt.setString(1, id.value)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    Dialog(
                        id = DialogId(rs.getString("id")),
                        title = rs.getString("title"),
                        messages = emptyList(), // Загрузим отдельно
                        createdAt = Instant.parse(rs.getString("created_at")),
                        updatedAt = Instant.parse(rs.getString("updated_at"))
                    )
                } else {
                    null
                }
            }
        }

        dialog?.let { d ->
            // Загружаем сообщения
            val messages = loadMessages(d.id)
            d.copy(messages = messages)
        }
    }

    override suspend fun findAll(): List<DialogSummary> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT d.id, d.title, d.updated_at, COUNT(m.id) as message_count
            FROM dialogs d
            LEFT JOIN messages m ON d.id = m.dialog_id
            GROUP BY d.id
            ORDER BY d.updated_at DESC
        """.trimIndent()

        connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                val result = mutableListOf<DialogSummary>()
                while (rs.next()) {
                    result.add(
                        DialogSummary(
                            id = DialogId(rs.getString("id")),
                            title = rs.getString("title"),
                            messageCount = rs.getInt("message_count"),
                            updatedAt = Instant.parse(rs.getString("updated_at"))
                        )
                    )
                }
                result
            }
        }
    }

    override suspend fun delete(id: DialogId): Unit = withContext(Dispatchers.IO) {
        connection.transaction {
            // Сначала удаляем сообщения (хотя CASCADE должен сработать)
            val deleteMessagesSql = "DELETE FROM messages WHERE dialog_id = ?"
            prepareStatement(deleteMessagesSql).use { stmt ->
                stmt.setString(1, id.value)
                stmt.executeUpdate()
            }

            // Затем удаляем диалог
            val deleteDialogSql = "DELETE FROM dialogs WHERE id = ?"
            prepareStatement(deleteDialogSql).use { stmt ->
                stmt.setString(1, id.value)
                stmt.executeUpdate()
            }
        }
    }

    /**
     * Загружает сообщения для диалога.
     */
    private fun loadMessages(dialogId: DialogId): List<ChatMessage> {
        val sql = """
            SELECT role, content, created_at FROM messages 
            WHERE dialog_id = ? 
            ORDER BY id ASC
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, dialogId.value)
            stmt.executeQuery().use { rs ->
                val messages = mutableListOf<ChatMessage>()
                while (rs.next()) {
                    val role = ChatRole.valueOf(rs.getString("role").uppercase())
                    val content = rs.getString("content")
                    val createdAt = Instant.parse(rs.getString("created_at"))
                    messages.add(ChatMessage(role, content, createdAt))
                }
                messages
            }
        }
    }

    /**
     * Выполняет блок кода в транзакции.
     */
    private inline fun <T> Connection.transaction(block: Connection.() -> T): T {
        val originalAutoCommit = autoCommit
        autoCommit = false
        return try {
            val result = block()
            commit()
            result
        } catch (e: Exception) {
            rollback()
            throw e
        } finally {
            autoCommit = originalAutoCommit
        }
    }

    /**
     * Закрывает соединение с БД.
     */
    fun close() {
        if (!connection.isClosed) {
            connection.close()
        }
    }
}

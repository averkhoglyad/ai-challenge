package io.averkhogliad.ai.challenge.week1.infrastructure.persistence

import io.averkhogliad.ai.challenge.week1.domain.model.*
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

            // Таблица для хранения summary диалогов (сжатый контекст)
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS dialog_summaries (
                    dialog_id TEXT PRIMARY KEY,
                    accumulated_summary TEXT,
                    compressed_message_count INTEGER NOT NULL DEFAULT 0,
                    last_updated TEXT NOT NULL,
                    FOREIGN KEY (dialog_id) REFERENCES dialogs(id) ON DELETE CASCADE
                )
            """.trimIndent()
            )

            // Добавляем колонку message_tags, если ещё не добавлена (миграция)
            try {
                stmt.execute("ALTER TABLE dialogs ADD COLUMN message_tags TEXT DEFAULT '{}'")
            } catch (_: Exception) {
                // Колонка уже существует — игнорируем
            }
        }
    }

    override suspend fun save(dialog: Dialog): Unit = withContext(Dispatchers.IO) {
        connection.transaction {
            // Сохраняем или обновляем диалог
            val dialogSql = """
                INSERT OR REPLACE INTO dialogs (id, title, created_at, updated_at, message_tags)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()

            prepareStatement(dialogSql).use { stmt ->
                stmt.setString(1, dialog.id.value)
                stmt.setString(2, dialog.title)
                stmt.setString(3, dialog.createdAt.toString())
                stmt.setString(4, dialog.updatedAt.toString())
                stmt.setString(5, serializeMessageTags(dialog.messageTags))
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

            // Сохраняем accumulatedSummary если он есть
            if (dialog.accumulatedSummary != null) {
                saveSummaryInternal(dialog.id, dialog.accumulatedSummary, dialog.compressedMessageCount)
            }
        }
    }

    override suspend fun findById(id: DialogId): Dialog? = withContext(Dispatchers.IO) {
        // Загружаем диалог
        val dialogSql = "SELECT id, title, created_at, updated_at, message_tags FROM dialogs WHERE id = ?"
        val dialog = connection.prepareStatement(dialogSql).use { stmt ->
            stmt.setString(1, id.value)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    Dialog(
                        id = DialogId(rs.getString("id")),
                        title = rs.getString("title"),
                        messages = emptyList(), // Загрузим отдельно
                        createdAt = Instant.parse(rs.getString("created_at")),
                        updatedAt = Instant.parse(rs.getString("updated_at")),
                        messageTags = deserializeMessageTags(rs.getString("message_tags"))
                    )
                } else {
                    null
                }
            }
        }

        dialog?.let { d ->
            // Загружаем сообщения
            val messages = loadMessages(d.id)
            // Загружаем accumulatedSummary
            val summary = loadSummary(d.id)
            d.copy(
                messages = messages,
                accumulatedSummary = summary?.accumulatedSummary
            )
        }
    }

    override suspend fun findAll(): List<DialogSummary> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT d.id, d.title, d.updated_at, d.message_tags,
                   COUNT(m.id) as message_count,
                   s.accumulated_summary, s.compressed_message_count
            FROM dialogs d
            LEFT JOIN messages m ON d.id = m.dialog_id
            LEFT JOIN dialog_summaries s ON d.id = s.dialog_id
            GROUP BY d.id
            ORDER BY d.updated_at DESC
        """.trimIndent()

        connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                val result = mutableListOf<DialogSummary>()
                while (rs.next()) {
                    val tags = deserializeMessageTags(rs.getString("message_tags"))
                    result.add(
                        DialogSummary(
                            id = DialogId(rs.getString("id")),
                            title = rs.getString("title"),
                            messageCount = rs.getInt("message_count"),
                            updatedAt = Instant.parse(rs.getString("updated_at")),
                            accumulatedSummary = rs.getString("accumulated_summary"),
                            tagStats = TagStats.fromMessageTags(tags)
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
     * Сохраняет summary для диалога (публичный suspend метод).
     *
     * @param dialogId идентификатор диалога
     * @param summary текст суммаризации
     * @param compressedCount количество сжатых сообщений
     */
    suspend fun saveSummary(dialogId: DialogId, summary: String, compressedCount: Int): Unit =
        withContext(Dispatchers.IO) {
            saveSummaryInternal(dialogId, summary, compressedCount)
        }

    /**
     * Загружает summary для диалога.
     *
     * @param dialogId идентификатор диалога
     * @return [DialogSummary] с summary данными или null, если summary не сохранено
     */
    suspend fun loadSummary(dialogId: DialogId): DialogSummary? = withContext(Dispatchers.IO) {
        val sql = """
            SELECT ds.dialog_id, ds.accumulated_summary, ds.compressed_message_count, d.updated_at
            FROM dialog_summaries ds
            JOIN dialogs d ON ds.dialog_id = d.id
            WHERE ds.dialog_id = ?
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, dialogId.value)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    DialogSummary(
                        id = DialogId(rs.getString("dialog_id")),
                        title = "Summary", // Placeholder — DialogSummary requires non-blank title
                        messageCount = 0, // Не используется в этом контексте
                        updatedAt = Instant.parse(rs.getString("updated_at")),
                        accumulatedSummary = rs.getString("accumulated_summary")
                    )
                } else {
                    null
                }
            }
        }
    }

    /**
     * Внутренний метод сохранения summary (вызывается в транзакции).
     */
    private fun saveSummaryInternal(dialogId: DialogId, summary: String, compressedCount: Int) {
        val sql = """
            INSERT OR REPLACE INTO dialog_summaries (dialog_id, accumulated_summary, compressed_message_count, last_updated)
            VALUES (?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, dialogId.value)
            stmt.setString(2, summary)
            stmt.setInt(3, compressedCount)
            stmt.setString(4, Instant.now().toString())
            stmt.executeUpdate()
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

    // ═══════════════════════════════════════════════════════════════
    // Сериализация messageTags в/из JSON для хранения в SQLite
    // ═══════════════════════════════════════════════════════════════

    private fun serializeMessageTags(tags: Map<Int, Set<MessageTag>>): String {
        if (tags.isEmpty()) return "{}"
        val sb = StringBuilder("{")
        tags.entries.forEachIndexed { i, (index, tagSet) ->
            if (i > 0) sb.append(",")
            sb.append("\"$index\":[")
            tagSet.joinTo(sb, separator = ",") { "\"${it.key}\"" }
            sb.append("]")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun deserializeMessageTags(json: String?): Map<Int, Set<MessageTag>> {
        if (json.isNullOrBlank() || json == "{}") return emptyMap()
        val result = mutableMapOf<Int, Set<MessageTag>>()
        // Простой парсер без зависимостей: {"0":["compressed"],"1":["fact"]}
        val trimmed = json.trim().removeSurrounding("{", "}")
        if (trimmed.isBlank()) return emptyMap()
        val entries = splitJsonEntries(trimmed)
        for (entry in entries) {
            val colonIndex = entry.indexOf(':')
            if (colonIndex == -1) continue
            val key = entry.substring(0, colonIndex).trim().removeSurrounding("\"")
            val value = entry.substring(colonIndex + 1).trim()
            val index = key.toIntOrNull() ?: continue
            val tags = parseTagArray(value)
            if (tags.isNotEmpty()) result[index] = tags
        }
        return result
    }

    private fun splitJsonEntries(content: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        var inString = false
        for (i in content.indices) {
            val c = content[i]
            if (c == '"' && (i == 0 || content[i - 1] != '\\')) inString = !inString
            if (inString) continue
            when (c) {
                '{', '[' -> depth++
                '}', ']' -> depth--
                ',' -> if (depth == 0) {
                    result.add(content.substring(start, i))
                    start = i + 1
                }
            }
        }
        if (start < content.length) result.add(content.substring(start))
        return result
    }

    private fun parseTagArray(json: String): Set<MessageTag> {
        val trimmed = json.trim().removeSurrounding("[", "]")
        if (trimmed.isBlank()) return emptySet()
        val items = trimmed.split(",").map { it.trim().removeSurrounding("\"") }
        return items.mapNotNull { key ->
            when (key) {
                MessageTag.Compressed.key -> MessageTag.Compressed
                MessageTag.FactExtraction.key -> MessageTag.FactExtraction
                MessageTag.BranchPoint.key -> MessageTag.BranchPoint
                MessageTag.Checkpoint.key -> MessageTag.Checkpoint
                else -> null
            }
        }.toSet()
    }
}

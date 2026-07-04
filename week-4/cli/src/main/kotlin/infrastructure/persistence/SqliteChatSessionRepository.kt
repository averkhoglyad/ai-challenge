package io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence

import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatSessionRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import java.time.Instant
import java.util.*

/**
 * Реализация [ChatSessionRepository] и [TaskStateRepository] на SQLite через JDBC.
 *
 * ## Архитектурная роль
 * - **Infrastructure Layer** — реализует domain-порты [ChatSessionRepository] и [TaskStateRepository]
 * - **Hexagonal Architecture** — адаптер для persistence
 *
 * ## Схема БД
 * - Таблица `chat_sessions`: id, name, name_generated, archived, active, config_json, task_state_json, created_at, updated_at
 * - Таблица `chat_messages`: id, session_id, type, content, citations_json, sources_json, timestamp
 *
 * ## Особенности
 * - Автоматическое создание схемы при инициализации
 * - Файл БД по умолчанию: ~/.ai-challenge/chat.db
 * - Поддержка транзакций для атомарности операций (save, setActive, delete)
 * - Сериализация ChatConfig, TaskState, ChatSource — через kotlinx.serialization
 *
 * @param database единый владелец SQLite JDBC-соединения
 */
class SqliteChatSessionRepository(
    private val database: SqliteDatabase
) : ChatSessionRepository {

    private val connection get() = database.connection

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        initializeSchema()
    }

    // ════════════════════════════════════════════════════════════════
    // Schema initialization
    // ════════════════════════════════════════════════════════════════

    /**
     * Создаёт таблицы БД для чат-сессий и сообщений, если они не существуют.
     */
    private fun initializeSchema() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS chat_sessions (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL DEFAULT 'New Chat',
                    name_generated INTEGER NOT NULL DEFAULT 0,
                    archived INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 0,
                    config_json TEXT NOT NULL,
                    task_state_json TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """.trimIndent()
            )

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    type TEXT NOT NULL CHECK(type IN ('user', 'assistant', 'system')),
                    content TEXT NOT NULL,
                    citations_json TEXT,
                    sources_json TEXT,
                    timestamp TEXT NOT NULL,
                    FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_chat_messages_session_id 
                ON chat_messages(session_id)
                """.trimIndent()
            )

            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_chat_sessions_active 
                ON chat_sessions(active)
                """.trimIndent()
            )
        }
    }

    // ════════════════════════════════════════════════════════════════
    // ChatSessionRepository
    // ════════════════════════════════════════════════════════════════

    override suspend fun save(session: ChatSession): Result<ChatSession> = runCatching {
        connection.autoCommit = false
        try {
            val configJson = json.encodeToString(ChatConfigDto.from(session.config))
            val taskStateJson = json.encodeToString(TaskStateDto.from(session.taskState))

            val sessionSql = """
                INSERT OR REPLACE INTO chat_sessions 
                (id, name, name_generated, archived, active, config_json, task_state_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            connection.prepareStatement(sessionSql).use { stmt ->
                stmt.setString(1, session.metadata.id.toString())
                stmt.setString(2, session.metadata.name)
                stmt.setInt(3, if (session.metadata.nameGenerated) 1 else 0)
                stmt.setInt(4, if (session.metadata.archived) 1 else 0)
                stmt.setInt(5, if (session.metadata.active) 1 else 0)
                stmt.setString(6, configJson)
                stmt.setString(7, taskStateJson)
                stmt.setString(8, session.metadata.createdAt.toString())
                stmt.setString(9, session.metadata.updatedAt.toString())
                stmt.executeUpdate()
            }

            // Вставляем только новые сообщения (по id через INSERT OR IGNORE)
            if (session.messages.isNotEmpty()) {
                val messageSql = """
                    INSERT OR IGNORE INTO chat_messages (id, session_id, type, content, citations_json, sources_json, timestamp)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()

                connection.prepareStatement(messageSql).use { stmt ->
                    for (msg in session.messages) {
                        stmt.setString(1, msg.id.toString())
                        stmt.setString(2, msg.sessionId.toString())
                        stmt.setString(3, messageTypeToString(msg))
                        stmt.setString(4, messageContent(msg))
                        stmt.setString(5, serializeCitations(msg))
                        stmt.setString(6, serializeSources(msg))
                        stmt.setString(7, msg.createdAt.toString())
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
            }

            connection.commit()
            session
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override suspend fun loadActive(): Result<ChatSession?> = runCatching {
        val sessionSql = """
            SELECT id, name, name_generated, archived, active, config_json, task_state_json, created_at, updated_at
            FROM chat_sessions
            WHERE active = 1
            ORDER BY updated_at DESC
            LIMIT 1
        """.trimIndent()

        val session = connection.createStatement().use { stmt ->
            stmt.executeQuery(sessionSql).use { rs ->
                if (rs.next()) mapRowToChatSession(rs) else null
            }
        } ?: return@runCatching null

        val messages = loadMessagesForSession(session.metadata.id)
        session.copy(messages = messages)
    }

    override suspend fun loadById(id: UUID): Result<ChatSession?> = runCatching {
        val sessionSql = """
            SELECT id, name, name_generated, archived, active, config_json, task_state_json, created_at, updated_at
            FROM chat_sessions
            WHERE id = ?
        """.trimIndent()

        val session = connection.prepareStatement(sessionSql).use { stmt ->
            stmt.setString(1, id.toString())
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapRowToChatSession(rs) else null
            }
        } ?: return@runCatching null

        val messages = loadMessagesForSession(id)
        session.copy(messages = messages)
    }

    override suspend fun listSessions(): Result<List<ChatSession>> = runCatching {
        val sql = """
            SELECT id, name, name_generated, archived, active, config_json, task_state_json, created_at, updated_at
            FROM chat_sessions
            WHERE archived = 0
            ORDER BY updated_at DESC
        """.trimIndent()

        connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                val sessions = mutableListOf<ChatSession>()
                while (rs.next()) {
                    sessions.add(mapRowToChatSession(rs))
                }
                sessions
            }
        }
    }

    override suspend fun setActive(id: UUID): Result<Unit> = runCatching {
        connection.autoCommit = false
        try {
            // Сбросить active у всех сессий
            connection.createStatement().use { stmt ->
                stmt.executeUpdate("UPDATE chat_sessions SET active = 0")
            }

            // Установить active = 1 у целевой
            connection.prepareStatement("UPDATE chat_sessions SET active = 1, updated_at = ? WHERE id = ?")
                .use { stmt ->
                    stmt.setString(1, Instant.now().toString())
                    stmt.setString(2, id.toString())
                    val updated = stmt.executeUpdate()
                    if (updated == 0) {
                        throw NoSuchElementException("Chat session not found: $id")
                    }
                }

            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override suspend fun archiveSession(id: UUID): Result<Unit> = runCatching {
        connection.prepareStatement(
            "UPDATE chat_sessions SET archived = 1, active = 0, updated_at = ? WHERE id = ?"
        ).use { stmt ->
            stmt.setString(1, Instant.now().toString())
            stmt.setString(2, id.toString())
            val updated = stmt.executeUpdate()
            if (updated == 0) {
                throw NoSuchElementException("Chat session not found: $id")
            }
        }
    }

    override suspend fun deleteSession(id: UUID): Result<Unit> = runCatching {
        connection.autoCommit = false
        try {
            // Сначала удаляем сообщения (из-за FOREIGN KEY с ON DELETE CASCADE это опционально, но делаем явно)
            connection.prepareStatement("DELETE FROM chat_messages WHERE session_id = ?").use { stmt ->
                stmt.setString(1, id.toString())
                stmt.executeUpdate()
            }

            connection.prepareStatement("DELETE FROM chat_sessions WHERE id = ?").use { stmt ->
                stmt.setString(1, id.toString())
                val deleted = stmt.executeUpdate()
                if (deleted == 0) {
                    throw NoSuchElementException("Chat session not found: $id")
                }
            }

            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    // ════════════════════════════════════════════════════════════════
    // TaskState persistence (internal, not a port)
    // ════════════════════════════════════════════════════════════════

    suspend fun save(sessionId: UUID, state: TaskState): Result<Unit> = runCatching {
        val taskStateJson = json.encodeToString(TaskStateDto.from(state))
        connection.prepareStatement(
            "UPDATE chat_sessions SET task_state_json = ?, updated_at = ? WHERE id = ?"
        ).use { stmt ->
            stmt.setString(1, taskStateJson)
            stmt.setString(2, Instant.now().toString())
            stmt.setString(3, sessionId.toString())
            val updated = stmt.executeUpdate()
            if (updated == 0) {
                throw NoSuchElementException("Chat session not found: $sessionId")
            }
        }
    }

    suspend fun load(sessionId: UUID): Result<TaskState?> = runCatching {
        connection.prepareStatement(
            "SELECT task_state_json FROM chat_sessions WHERE id = ?"
        ).use { stmt ->
            stmt.setString(1, sessionId.toString())
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val jsonStr = rs.getString("task_state_json")
                    if (jsonStr.isNullOrBlank()) null
                    else json.decodeFromString<TaskStateDto>(jsonStr).toDomain()
                } else {
                    null
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Private helpers — row mapping
    // ════════════════════════════════════════════════════════════════

    /** Маппит строку ResultSet из chat_sessions в [ChatSession] без сообщений. */
    private fun mapRowToChatSession(rs: ResultSet): ChatSession {
        val configJson = rs.getString("config_json")
        val taskStateJson = rs.getString("task_state_json")

        val config = if (configJson.isNullOrBlank()) {
            ChatConfig()
        } else {
            json.decodeFromString<ChatConfigDto>(configJson).toDomain()
        }

        val taskState = if (taskStateJson.isNullOrBlank()) {
            TaskState.EMPTY
        } else {
            json.decodeFromString<TaskStateDto>(taskStateJson).toDomain()
        }

        return ChatSession(
            metadata = ChatMetadata(
                id = UUID.fromString(rs.getString("id")),
                name = rs.getString("name"),
                nameGenerated = rs.getInt("name_generated") == 1,
                createdAt = Instant.parse(rs.getString("created_at")),
                updatedAt = Instant.parse(rs.getString("updated_at")),
                archived = rs.getInt("archived") == 1,
                active = rs.getInt("active") == 1
            ),
            messages = emptyList(), // Сообщения загружаются отдельно
            taskState = taskState,
            config = config
        )
    }

    /** Загружает все сообщения для указанной сессии. */
    private fun loadMessagesForSession(sessionId: UUID): List<ChatMessage> {
        val sql = """
            SELECT id, session_id, type, content, citations_json, sources_json, timestamp
            FROM chat_messages
            WHERE session_id = ?
            ORDER BY timestamp ASC
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, sessionId.toString())
            stmt.executeQuery().use { rs ->
                val messages = mutableListOf<ChatMessage>()
                while (rs.next()) {
                    messages.add(mapRowToChatMessage(rs))
                }
                messages
            }
        }
    }

    /** Маппит строку ResultSet из chat_messages в [ChatMessage]. */
    private fun mapRowToChatMessage(rs: ResultSet): ChatMessage {
        val id = UUID.fromString(rs.getString("id"))
        val sid = UUID.fromString(rs.getString("session_id"))
        val type = rs.getString("type")
        val content = rs.getString("content")
        val timestamp = Instant.parse(rs.getString("timestamp"))

        return when (type) {
            "user" -> ChatMessage.User(
                id = id,
                sessionId = sid,
                text = content,
                createdAt = timestamp
            )

            "assistant" -> {
                val citationsJson = rs.getString("citations_json")
                val sourcesJson = rs.getString("sources_json")
                val citations = parseCitationsJson(citationsJson)
                val sources = parseSourcesJson(sourcesJson)
                ChatMessage.Assistant(
                    id = id,
                    sessionId = sid,
                    text = content,
                    citations = citations,
                    sources = sources,
                    createdAt = timestamp
                )
            }

            "system" -> ChatMessage.System(
                id = id,
                sessionId = sid,
                text = content,
                createdAt = timestamp
            )

            else -> ChatMessage.System(
                id = id,
                sessionId = sid,
                text = content,
                createdAt = timestamp
            )
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Message serialization helpers
    // ════════════════════════════════════════════════════════════════

    private fun messageTypeToString(msg: ChatMessage): String = when (msg) {
        is ChatMessage.User -> "user"
        is ChatMessage.Assistant -> "assistant"
        is ChatMessage.System -> "system"
    }

    private fun messageContent(msg: ChatMessage): String = when (msg) {
        is ChatMessage.User -> msg.text
        is ChatMessage.Assistant -> msg.text
        is ChatMessage.System -> msg.text
    }

    private fun serializeCitations(msg: ChatMessage): String? = when (msg) {
        is ChatMessage.Assistant ->
            if (msg.citations.isEmpty()) null
            else json.encodeToString(msg.citations)

        else -> null
    }

    private fun serializeSources(msg: ChatMessage): String? = when (msg) {
        is ChatMessage.Assistant ->
            if (msg.sources.isEmpty()) null
            else json.encodeToString(msg.sources.map { ChatSourceDto.from(it) })

        else -> null
    }

    private fun parseCitationsJson(jsonStr: String?): List<Int> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<Int>>(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSourcesJson(jsonStr: String?): List<ChatSource> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<ChatSourceDto>>(jsonStr).map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Serializable DTOs for JSON persistence
    // ════════════════════════════════════════════════════════════════

    @Serializable
    private data class ChatConfigDto(
        val historyWindowSize: Int = 6,
        val nameMaxLength: Int = 50,
        val autoNameEnabled: Boolean = true,
        val taskStateExtractionEnabled: Boolean = true,
        val taskStateMaxTerms: Int = 50,
        val taskStateMaxConstraints: Int = 50,
        val maxClarifiedFacts: Int = 50
    ) {
        fun toDomain(): ChatConfig = ChatConfig(
            historyWindowSize = historyWindowSize,
            nameMaxLength = nameMaxLength,
            autoNameEnabled = autoNameEnabled,
            taskStateExtractionEnabled = taskStateExtractionEnabled,
            taskStateMaxTerms = taskStateMaxTerms,
            taskStateMaxConstraints = taskStateMaxConstraints,
            maxClarifiedFacts = maxClarifiedFacts
        )

        companion object {
            fun from(config: ChatConfig): ChatConfigDto = ChatConfigDto(
                historyWindowSize = config.historyWindowSize,
                nameMaxLength = config.nameMaxLength,
                autoNameEnabled = config.autoNameEnabled,
                taskStateExtractionEnabled = config.taskStateExtractionEnabled,
                taskStateMaxTerms = config.taskStateMaxTerms,
                taskStateMaxConstraints = config.taskStateMaxConstraints,
                maxClarifiedFacts = config.maxClarifiedFacts
            )
        }
    }

    @Serializable
    private data class TaskStateDto(
        val goal: String? = null,
        val definedTerms: List<TermEntryDto> = emptyList(),
        val constraints: List<String> = emptyList(),
        val clarifiedFacts: List<String> = emptyList(),
        val lastUpdated: String = Instant.now().toString()
    ) {
        fun toDomain(): TaskState = TaskState(
            goal = goal,
            definedTerms = definedTerms.map { it.name to it.definition },
            constraints = constraints,
            clarifiedFacts = clarifiedFacts,
            lastUpdated = try {
                Instant.parse(lastUpdated)
            } catch (_: Exception) {
                Instant.now()
            }
        )

        companion object {
            fun from(state: TaskState): TaskStateDto = TaskStateDto(
                goal = state.goal,
                definedTerms = state.definedTerms.map { TermEntryDto(it.first, it.second) },
                constraints = state.constraints,
                clarifiedFacts = state.clarifiedFacts,
                lastUpdated = state.lastUpdated.toString()
            )
        }
    }

    @Serializable
    private data class TermEntryDto(
        val name: String,
        val definition: String
    )

    @Serializable
    private data class ChatSourceDto(
        val citationNumber: Int,
        val documentId: String,
        val documentName: String,
        val relevance: Float
    ) {
        fun toDomain(): ChatSource = ChatSource(
            citationNumber = citationNumber,
            documentId = documentId,
            documentName = documentName,
            relevance = relevance
        )

        companion object {
            fun from(source: ChatSource): ChatSourceDto = ChatSourceDto(
                citationNumber = source.citationNumber,
                documentId = source.documentId,
                documentName = source.documentName,
                relevance = source.relevance
            )
        }
    }

}

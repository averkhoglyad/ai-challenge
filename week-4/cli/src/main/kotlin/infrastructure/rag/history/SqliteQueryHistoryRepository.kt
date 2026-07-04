package io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.history

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.QueryHistoryRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.Statement
import java.time.Instant
import java.util.*

/**
 * Реализация [QueryHistoryRepository] на SQLite через JDBC.
 *
 * Хранит историю RAG-запросов в таблице `query_history`.
 * [RagAnswer] и [SearchContext] сериализуются в JSON через kotlinx.serialization.
 *
 * Таблица создаётся идемпотентно при инициализации репозитория.
 */
class SqliteQueryHistoryRepository(
    private val database: SqliteDatabase
) : QueryHistoryRepository {

    private val connection: Connection get() = database.connection
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    init {
        initializeTable()
    }

    private fun initializeTable() {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate(CREATE_TABLE)
            stmt.executeUpdate(CREATE_INDEX_TIMESTAMP)
            stmt.executeUpdate(CREATE_INDEX_MODE)
        }
    }

    override suspend fun save(entry: QueryHistoryEntry): Long {
        val sql = """
            INSERT INTO query_history (query, answer, search_context, mode, total_time_ms, token_usage, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, entry.query)
            ps.setString(2, json.encodeToString(RagAnswerDto(answer = entry.answer.answer)))
            ps.setString(3, json.encodeToString(toContextDto(entry.searchContext)))
            ps.setString(4, entry.searchContext.stats.mode.toString())
            ps.setLong(5, entry.searchContext.stats.totalMs)
            ps.setInt(6, entry.searchContext.stats.tokens.total)
            ps.setLong(7, entry.timestamp.toEpochMilli())
            ps.executeUpdate()

            val rs = ps.generatedKeys
            if (rs.next()) rs.getLong(1)
            else connection.createStatement().use { stmt ->
                val idRs = stmt.executeQuery("SELECT last_insert_rowid()")
                if (idRs.next()) idRs.getLong(1) else -1L
            }
        }
    }

    override suspend fun getLast(limit: Int): List<QueryHistoryEntry> {
        val sql = "SELECT * FROM query_history ORDER BY timestamp DESC LIMIT ?"
        return queryList(sql) { ps -> ps.setInt(1, limit) }
    }

    override suspend fun getById(id: Long): QueryHistoryEntry? {
        val sql = "SELECT * FROM query_history WHERE id = ?"
        return connection.prepareStatement(sql).use { ps ->
            ps.setLong(1, id)
            val rs = ps.executeQuery()
            if (rs.next()) mapRow(rs) else null
        }
    }

    override suspend fun deleteAll() {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate("DELETE FROM query_history")
        }
    }

    override suspend fun count(): Int {
        return connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT COUNT(*) FROM query_history")
            if (rs.next()) rs.getInt(1) else 0
        }
    }

    private fun queryList(
        sql: String,
        setParams: (java.sql.PreparedStatement) -> Unit
    ): List<QueryHistoryEntry> {
        val entries = mutableListOf<QueryHistoryEntry>()
        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            setParams(ps)
            val rs = ps.executeQuery()
            while (rs.next()) {
                entries.add(mapRow(rs))
            }
        }
        return entries
    }

    private fun mapRow(rs: java.sql.ResultSet): QueryHistoryEntry {
        val id = rs.getLong("id")
        val query = rs.getString("query")
        val answerJson = rs.getString("answer")
        val contextJson = rs.getString("search_context")
        val timestamp = Instant.ofEpochMilli(rs.getLong("timestamp"))
        val totalTimeMs = rs.getLong("total_time_ms")
        val tokenUsage = rs.getInt("token_usage")
        val modeStr = rs.getString("mode")

        val answerDto = json.decodeFromString<RagAnswerDto>(answerJson)
        val contextDto = json.decodeFromString<SearchContextDto>(contextJson)

        val mode = parseSearchMode(modeStr)

        return QueryHistoryEntry(
            id = id,
            query = query,
            answer = RagAnswer(answer = answerDto.answer),
            searchContext = SearchContext(
                query = contextDto.query,
                rewrittenQuery = contextDto.rewrittenQuery,
                rawResults = emptyList(),
                filteredResults = emptyList(),
                droppedChunks = emptyList(),
                stats = QueryExecutionStats(
                    queryId = UUID.fromString(contextDto.queryId),
                    timestamp = Instant.ofEpochMilli(contextDto.timestamp),
                    mode = mode,
                    totalMs = totalTimeMs,
                    chunks = ChunkFlow(0, 0, 0),
                    score = ScoreDelta(0f, 0f),
                    tokens = TokenBreakdown(null, null, tokenUsage),
                    dropped = DropBreakdown(0, 0, 0)
                )
            ),
            timestamp = timestamp
        )
    }

    private fun parseSearchMode(modeStr: String): SearchMode = when (modeStr) {
        "Raw" -> SearchMode.Raw
        "Reranked" -> SearchMode.Reranked
        "Rewrite" -> SearchMode.Rewrite
        else -> SearchMode.Filtered
    }

    private fun toContextDto(context: SearchContext): SearchContextDto = SearchContextDto(
        query = context.query,
        rewrittenQuery = context.rewrittenQuery,
        queryId = context.stats.queryId.toString(),
        timestamp = context.stats.timestamp.toEpochMilli()
    )

    @Serializable
    private data class RagAnswerDto(val answer: String)

    @Serializable
    private data class SearchContextDto(
        val query: String,
        val rewrittenQuery: String? = null,
        val queryId: String,
        val timestamp: Long
    )

    companion object {
        val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS query_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                query TEXT NOT NULL,
                answer TEXT NOT NULL,
                search_context TEXT NOT NULL,
                mode TEXT NOT NULL,
                total_time_ms INTEGER NOT NULL,
                token_usage INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """.trimIndent()

        val CREATE_INDEX_TIMESTAMP = """
            CREATE INDEX IF NOT EXISTS idx_query_history_timestamp ON query_history(timestamp)
        """.trimIndent()

        val CREATE_INDEX_MODE = """
            CREATE INDEX IF NOT EXISTS idx_query_history_mode ON query_history(mode)
        """.trimIndent()
    }
}

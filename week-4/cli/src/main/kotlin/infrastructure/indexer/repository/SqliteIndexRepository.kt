package io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.repository

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.port.IndexRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDatabase
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.sql.Connection
import java.time.Instant
import java.util.*

/**
 * SQLite-реализация [IndexRepository].
 *
 * Использует существующее подключение [SqliteDatabase].
 * Сериализует `FloatArray` в BLOB через [ByteBuffer].
 * Хранит `Map<String, String>` как JSON-строку.
 */
class SqliteIndexRepository(
    private val database: SqliteDatabase
) : IndexRepository {

    private val connection: Connection get() = database.connection
    private val json = Json { encodeDefaults = false }

    // ──── Runs ────

    override suspend fun createRun(run: IndexingRun) {
        connection.prepareStatement(INSERT_RUN).use { stmt ->
            stmt.setString(1, run.id.toString())
            stmt.setLong(2, run.startedAt.toEpochMilli())
            if (run.finishedAt != null) stmt.setLong(3, run.finishedAt.toEpochMilli())
            else stmt.setNull(3, java.sql.Types.BIGINT)
            stmt.setString(4, run.strategy.name)
            stmt.setString(5, run.sourcePath)
            if (run.chunkSize != null) stmt.setInt(6, run.chunkSize)
            else stmt.setNull(6, java.sql.Types.INTEGER)
            if (run.overlap != null) stmt.setInt(7, run.overlap)
            else stmt.setNull(7, java.sql.Types.INTEGER)
            stmt.setString(8, run.embeddingModel)
            stmt.setString(9, run.status.name)
            stmt.setInt(10, run.totalChunks)
            stmt.setString(11, run.errorMessage)
            stmt.setString(12, json.encodeToString(run.metadata))
            stmt.executeUpdate()
        }
    }

    override suspend fun updateRunStatus(
        runId: UUID,
        status: RunStatus,
        totalChunks: Int,
        errorMessage: String?
    ) {
        connection.prepareStatement(UPDATE_RUN_STATUS).use { stmt ->
            stmt.setString(1, status.name)
            stmt.setInt(2, totalChunks)
            stmt.setString(3, errorMessage)
            val finishedAt = if (status != RunStatus.RUNNING) Instant.now().toEpochMilli() else null
            if (finishedAt != null) stmt.setLong(4, finishedAt)
            else stmt.setNull(4, java.sql.Types.BIGINT)
            stmt.setString(5, runId.toString())
            stmt.executeUpdate()
        }
    }

    override suspend fun getRun(runId: UUID): IndexingRun? {
        return connection.prepareStatement(SELECT_RUN).use { stmt ->
            stmt.setString(1, runId.toString())
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toIndexingRun() else null
            }
        }
    }

    override suspend fun getAllRuns(): List<IndexingRun> {
        return connection.prepareStatement(SELECT_ALL_RUNS).use { stmt ->
            stmt.executeQuery().use { rs ->
                val runs = mutableListOf<IndexingRun>()
                while (rs.next()) runs.add(rs.toIndexingRun())
                runs
            }
        }
    }

    override suspend fun deleteRun(runId: UUID) {
        connection.prepareStatement(DELETE_RUN).use { stmt ->
            stmt.setString(1, runId.toString())
            stmt.executeUpdate()
        }
    }

    override suspend fun deleteRunsBefore(date: Instant) {
        connection.prepareStatement(DELETE_RUNS_BEFORE).use { stmt ->
            stmt.setLong(1, date.toEpochMilli())
            stmt.executeUpdate()
        }
    }

    override suspend fun keepLastRuns(count: Int) {
        connection.prepareStatement(KEEP_LAST_RUNS).use { stmt ->
            stmt.setInt(1, count)
            stmt.executeUpdate()
        }
    }

    override suspend fun deleteAllRunsExcept(activeRunId: UUID?) {
        if (activeRunId != null) {
            connection.prepareStatement(DELETE_ALL_EXCEPT).use { stmt ->
                stmt.setString(1, activeRunId.toString())
                stmt.executeUpdate()
            }
        } else {
            connection.prepareStatement(DELETE_ALL_RUNS).use { stmt ->
                stmt.executeUpdate()
            }
        }
    }

    // ──── Active index ────

    override suspend fun setActiveIndex(runId: UUID) {
        connection.prepareStatement(SET_ACTIVE_INDEX).use { stmt ->
            stmt.setString(1, runId.toString())
            stmt.setLong(2, Instant.now().toEpochMilli())
            stmt.executeUpdate()
        }
    }

    override suspend fun getActiveIndex(): UUID? {
        return connection.prepareStatement(GET_ACTIVE_INDEX).use { stmt ->
            stmt.executeQuery().use { rs ->
                if (rs.next()) UUID.fromString(rs.getString("run_id")) else null
            }
        }
    }

    // ──── Chunks ────

    override suspend fun saveBatch(chunks: List<IndexedChunk>) {
        connection.autoCommit = false
        try {
            connection.prepareStatement(INSERT_CHUNK).use { stmt ->
                for (item in chunks) {
                    stmt.setString(1, item.chunk.id.toString())
                    stmt.setString(2, item.chunk.runId.toString())
                    stmt.setString(3, item.chunk.contentHash)
                    stmt.setString(4, item.chunk.source)
                    stmt.setString(5, item.chunk.title)
                    stmt.setString(6, item.chunk.section)
                    stmt.setString(7, item.chunk.text)
                    stmt.setString(8, item.chunk.strategy.name)
                    stmt.setString(9, json.encodeToString(item.chunk.metadata))
                    stmt.setString(10, item.embedding.model)
                    stmt.setBytes(11, floatArrayToBytes(item.embedding.vector))
                    stmt.setLong(12, Instant.now().toEpochMilli())
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override suspend fun getChunksByRunId(runId: UUID): List<IndexedChunk> {
        return connection.prepareStatement(SELECT_CHUNKS_BY_RUN).use { stmt ->
            stmt.setString(1, runId.toString())
            stmt.executeQuery().use { rs ->
                val chunks = mutableListOf<IndexedChunk>()
                while (rs.next()) {
                    val chunk = Chunk(
                        id = UUID.fromString(rs.getString("id")),
                        runId = UUID.fromString(rs.getString("run_id")),
                        contentHash = rs.getString("content_hash"),
                        source = rs.getString("source"),
                        title = rs.getString("title"),
                        section = rs.getString("section"),
                        text = rs.getString("text"),
                        strategy = ChunkingStrategyType.valueOf(rs.getString("strategy")),
                        metadata = parseMetadata(rs.getString("metadata"))
                    )
                    val embedding = Embedding(
                        chunkId = chunk.id,
                        vector = bytesToFloatArray(rs.getBytes("embedding_vector")),
                        model = rs.getString("embedding_model")
                    )
                    chunks.add(IndexedChunk(chunk, embedding))
                }
                chunks
            }
        }
    }

    override suspend fun getStatistics(runId: UUID): IndexStatistics {
        return connection.prepareStatement(STATISTICS_QUERY).use { stmt ->
            stmt.setString(1, runId.toString())
            stmt.executeQuery().use { rs ->
                rs.next()
                val totalChunks = rs.getInt("total_chunks")
                if (totalChunks == 0) throw NoSuchElementException("No chunks found for run $runId")
                val avgSize = rs.getInt("avg_size")
                val minSize = rs.getInt("min_size")
                val maxSize = rs.getInt("max_size")

                val bySource = mutableMapOf<String, Int>()
                connection.prepareStatement(SOURCE_DISTRIBUTION_QUERY).use { srcStmt ->
                    srcStmt.setString(1, runId.toString())
                    srcStmt.executeQuery().use { srcRs ->
                        while (srcRs.next()) {
                            bySource[srcRs.getString("source")] = srcRs.getInt("count")
                        }
                    }
                }

                val run = getRun(runId)
                    ?: throw NoSuchElementException("Run $runId not found")

                val indexSizeBytes = connection.prepareStatement(INDEX_SIZE_QUERY).use { sizeStmt ->
                    sizeStmt.setString(1, runId.toString())
                    sizeStmt.executeQuery().use { sizeRs ->
                        if (sizeRs.next()) sizeRs.getLong("total_bytes") else 0L
                    }
                }

                IndexStatistics(
                    runId = runId,
                    strategy = run.strategy,
                    sourcePath = run.sourcePath,
                    totalChunks = totalChunks,
                    bySource = bySource,
                    avgChunkSize = avgSize,
                    minChunkSize = minSize,
                    maxChunkSize = maxSize,
                    indexSizeBytes = indexSizeBytes
                )
            }
        }
    }

    // ──── Serialization helpers ────

    private fun floatArrayToBytes(array: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(array.size * Float.SIZE_BYTES)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.asFloatBuffer().put(array)
        return buffer.array()
    }

    private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.BIG_ENDIAN)
        val floatBuffer = buffer.asFloatBuffer()
        return FloatArray(floatBuffer.limit()).also { floatBuffer.get(it) }
    }

    private fun parseMetadata(jsonStr: String?): Map<String, String> {
        if (jsonStr.isNullOrBlank() || jsonStr == "{}") return emptyMap()
        return try {
            json.decodeFromString(jsonStr)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // ──── ResultSet extension ────

    private fun java.sql.ResultSet.toIndexingRun(): IndexingRun {
        val finishedAtMillis = getLong("finished_at")
        val finishedWasNull = wasNull()
        return IndexingRun(
            id = UUID.fromString(getString("id")),
            startedAt = Instant.ofEpochMilli(getLong("started_at")),
            finishedAt = if (finishedWasNull || finishedAtMillis == 0L) null
            else Instant.ofEpochMilli(finishedAtMillis),
            strategy = ChunkingStrategyType.valueOf(getString("strategy")),
            sourcePath = getString("source_path"),
            chunkSize = getInt("chunk_size").takeIf { !wasNull() },
            overlap = getInt("overlap").takeIf { !wasNull() },
            embeddingModel = getString("embedding_model"),
            status = RunStatus.valueOf(getString("status")),
            totalChunks = getInt("total_chunks"),
            errorMessage = getString("error_message"),
            metadata = parseMetadata(getString("metadata"))
        )
    }

    // ──── SQL Statements ────

    companion object {
        val INSERT_RUN = """
            INSERT INTO indexing_runs (id, started_at, finished_at, strategy, source_path,
                chunk_size, overlap, embedding_model, status, total_chunks, error_message, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val UPDATE_RUN_STATUS = """
            UPDATE indexing_runs
            SET status = ?, total_chunks = ?, error_message = ?, finished_at = ?
            WHERE id = ?
        """.trimIndent()

        val SELECT_RUN = "SELECT * FROM indexing_runs WHERE id = ?"
        val SELECT_ALL_RUNS = "SELECT * FROM indexing_runs ORDER BY started_at DESC"
        val DELETE_RUN = "DELETE FROM indexing_runs WHERE id = ?"

        val DELETE_RUNS_BEFORE = "DELETE FROM indexing_runs WHERE started_at < ?"

        val KEEP_LAST_RUNS = """
            DELETE FROM indexing_runs WHERE id NOT IN (
                SELECT id FROM indexing_runs ORDER BY started_at DESC LIMIT ?
            )
        """.trimIndent()

        val DELETE_ALL_EXCEPT = "DELETE FROM indexing_runs WHERE id != ?"
        val DELETE_ALL_RUNS = "DELETE FROM indexing_runs"

        val SET_ACTIVE_INDEX = """
            INSERT OR REPLACE INTO active_index (id, run_id, activated_at) VALUES (1, ?, ?)
        """.trimIndent()

        val GET_ACTIVE_INDEX = "SELECT run_id FROM active_index WHERE id = 1"

        val INSERT_CHUNK = """
            INSERT INTO indexed_chunks (id, run_id, content_hash, source, title, section,
                text, strategy, metadata, embedding_model, embedding_vector, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val SELECT_CHUNKS_BY_RUN = "SELECT * FROM indexed_chunks WHERE run_id = ?"

        val STATISTICS_QUERY = """
            SELECT
                COUNT(*) AS total_chunks,
                COALESCE(AVG(LENGTH(text)), 0) AS avg_size,
                COALESCE(MIN(LENGTH(text)), 0) AS min_size,
                COALESCE(MAX(LENGTH(text)), 0) AS max_size
            FROM indexed_chunks WHERE run_id = ?
        """.trimIndent()

        val SOURCE_DISTRIBUTION_QUERY = """
            SELECT source, COUNT(*) AS count
            FROM indexed_chunks WHERE run_id = ?
            GROUP BY source
        """.trimIndent()

        val INDEX_SIZE_QUERY = """
            SELECT SUM(LENGTH(embedding_vector) + LENGTH(text)) AS total_bytes
            FROM indexed_chunks WHERE run_id = ?
        """.trimIndent()
    }
}

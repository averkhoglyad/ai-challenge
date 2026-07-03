package io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.repository

import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDatabase

/**
 * Владелец схемы таблиц индексатора.
 *
 * Создаёт таблицы `indexing_runs`, `indexed_chunks` и `active_index`
 * при инициализации, если они ещё не существуют.
 */
class IndexerDatabase(private val database: SqliteDatabase) {

    private val connection get() = database.connection

    /**
     * Создаёт все таблицы индексатора.
     * Идемпотентно (IF NOT EXISTS).
     */
    fun initialize() {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate(CREATE_INDEXING_RUNS)
            stmt.executeUpdate(CREATE_INDEXED_CHUNKS)
            stmt.executeUpdate(CREATE_ACTIVE_INDEX)
        }
    }

    companion object {
        val CREATE_INDEXING_RUNS = """
            CREATE TABLE IF NOT EXISTS indexing_runs (
                id TEXT PRIMARY KEY,
                started_at INTEGER NOT NULL,
                finished_at INTEGER,
                strategy TEXT NOT NULL CHECK(strategy IN ('FIXED_SIZE', 'STRUCTURAL')),
                source_path TEXT NOT NULL,
                chunk_size INTEGER,
                overlap INTEGER,
                embedding_model TEXT NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('RUNNING', 'COMPLETED', 'FAILED')),
                total_chunks INTEGER DEFAULT 0,
                error_message TEXT,
                metadata TEXT
            )
        """.trimIndent()

        val CREATE_INDEXED_CHUNKS = """
            CREATE TABLE IF NOT EXISTS indexed_chunks (
                id TEXT PRIMARY KEY,
                run_id TEXT NOT NULL,
                content_hash TEXT NOT NULL,
                source TEXT NOT NULL,
                title TEXT,
                section TEXT,
                text TEXT NOT NULL,
                strategy TEXT NOT NULL CHECK(strategy IN ('FIXED_SIZE', 'STRUCTURAL')),
                metadata TEXT,
                embedding_model TEXT NOT NULL,
                embedding_vector BLOB NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (run_id) REFERENCES indexing_runs(id) ON DELETE CASCADE
            )
        """.trimIndent()

        val CREATE_ACTIVE_INDEX = """
            CREATE TABLE IF NOT EXISTS active_index (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                run_id TEXT NOT NULL,
                activated_at INTEGER NOT NULL,
                FOREIGN KEY (run_id) REFERENCES indexing_runs(id) ON DELETE RESTRICT
            )
        """.trimIndent()
    }
}

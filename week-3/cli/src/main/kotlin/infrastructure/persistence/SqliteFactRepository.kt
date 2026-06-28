package io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Fact
import io.averkhogliad.ai.challenge.week3.cli.domain.model.FactId
import io.averkhogliad.ai.challenge.week3.cli.domain.service.FactRepository
import java.sql.ResultSet
import java.time.Instant

/**
 * Реализация [FactRepository] на SQLite с поддержкой полнотекстового поиска через FTS5.
 *
 * ## Архитектурная роль
 * - **Infrastructure Layer** — реализует port из domain-слоя
 * - **Hexagonal Architecture** — адаптер для persistence
 *
 * ## Схема БД
 * - Таблица `facts`: id, content, created_at
 * - Виртуальная таблица `facts_fts` (FTS5) для полнотекстового поиска
 * - Триггеры для автоматической синхронизации FTS с основной таблицей
 *
 * ## Особенности
 * - Автоматическое создание схемы при инициализации
 * - Файл БД задаётся через конструктор (общий с другими репозиториями)
 * - Поддержка транзакций для атомарности операций
 * - Полнотекстовый поиск через MATCH на FTS5
 *
 * @param database единый владелец SQLite JDBC-соединения
 */
class SqliteFactRepository(
    private val database: SqliteDatabase
) : FactRepository {

    private val connection get() = database.connection


    init {
        initializeSchema()
    }

    /**
     * Создаёт таблицы и триггеры для фактов, если они не существуют.
     */
    private fun initializeSchema() {
        connection.createStatement().use { stmt ->
            // Основная таблица фактов
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS facts (
                    id TEXT PRIMARY KEY,
                    content TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
                """.trimIndent()
            )

            // FTS5 виртуальная таблица для полнотекстового поиска
            stmt.execute(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS facts_fts USING fts5(
                    content,
                    content='facts',
                    content_rowid='rowid'
                )
                """.trimIndent()
            )

            // Триггеры для синхронизации FTS с основной таблицей
            stmt.execute(
                """
                CREATE TRIGGER IF NOT EXISTS facts_ai AFTER INSERT ON facts BEGIN
                    INSERT INTO facts_fts(rowid, content) VALUES (new.rowid, new.content);
                END
                """.trimIndent()
            )

            stmt.execute(
                """
                CREATE TRIGGER IF NOT EXISTS facts_ad AFTER DELETE ON facts BEGIN
                    INSERT INTO facts_fts(facts_fts, rowid, content) VALUES('delete', old.rowid, old.content);
                END
                """.trimIndent()
            )

            stmt.execute(
                """
                CREATE TRIGGER IF NOT EXISTS facts_au AFTER UPDATE ON facts BEGIN
                    INSERT INTO facts_fts(facts_fts, rowid, content) VALUES('delete', old.rowid, old.content);
                    INSERT INTO facts_fts(rowid, content) VALUES (new.rowid, new.content);
                END
                """.trimIndent()
            )
        }
    }

    override suspend fun save(fact: Fact): Fact {
        connection.autoCommit = false
        try {
            val sql = """
                INSERT OR REPLACE INTO facts (id, content, created_at)
                VALUES (?, ?, ?)
            """.trimIndent()

            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, fact.id.value)
                stmt.setString(2, fact.content)
                stmt.setString(3, fact.createdAt.toString())
                stmt.executeUpdate()
            }

            connection.commit()
            return fact
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override suspend fun findById(id: FactId): Fact? {
        val sql = """
            SELECT id, content, created_at
            FROM facts
            WHERE id = ?
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id.value)
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapRowToFact(rs) else null
            }
        }
    }

    override suspend fun findAll(): List<Fact> {
        val sql = """
            SELECT id, content, created_at
            FROM facts
            ORDER BY created_at DESC
        """.trimIndent()

        return connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                val facts = mutableListOf<Fact>()
                while (rs.next()) {
                    facts.add(mapRowToFact(rs))
                }
                facts
            }
        }
    }

    override suspend fun search(query: String): List<Fact> {
        // Экранируем спецсимволы FTS5 и добавляем префиксный поиск
        val sanitized = query.replace("\"", "\"\"")
        val ftsQuery = "\"$sanitized\""

        val sql = """
            SELECT f.id, f.content, f.created_at
            FROM facts f
            INNER JOIN facts_fts fts ON f.rowid = fts.rowid
            WHERE facts_fts MATCH ?
            ORDER BY rank
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, ftsQuery)
            stmt.executeQuery().use { rs ->
                val facts = mutableListOf<Fact>()
                while (rs.next()) {
                    facts.add(mapRowToFact(rs))
                }
                facts
            }
        }
    }

    override suspend fun searchBatch(queries: List<String>): List<Fact> {
        if (queries.isEmpty()) return emptyList()

        // Build a single FTS5 query with OR between terms
        val sanitizedQueries = queries.map { it.replace("\"", "\"\"") }
        // FTS5 MATCH: terms joined with OR
        val ftsQuery = sanitizedQueries.joinToString(" OR ") { "\"$it\"" }

        val sql = """
            SELECT DISTINCT f.id, f.content, f.created_at
            FROM facts f
            INNER JOIN facts_fts fts ON f.rowid = fts.rowid
            WHERE facts_fts MATCH ?
            ORDER BY rank
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, ftsQuery)
            stmt.executeQuery().use { rs ->
                val facts = mutableSetOf<Fact>()
                while (rs.next()) {
                    facts.add(mapRowToFact(rs))
                }
                facts.toList()
            }
        }
    }

    override suspend fun delete(id: FactId): Boolean {
        connection.autoCommit = false
        try {
            val sql = "DELETE FROM facts WHERE id = ?"
            val deleted = connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id.value)
                stmt.executeUpdate()
            }
            connection.commit()
            return deleted > 0
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override suspend fun count(): Int {
        val sql = "SELECT COUNT(*) FROM facts"
        return connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }
    }

    /**
     * Маппит строку ResultSet в доменную модель Fact.
     */
    private fun mapRowToFact(rs: ResultSet): Fact {
        return Fact(
            id = FactId(rs.getString("id")),
            content = rs.getString("content"),
            createdAt = Instant.parse(rs.getString("created_at"))
        )
    }


}

package io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Invariant
import io.averkhogliad.ai.challenge.week3.cli.domain.model.InvariantId
import io.averkhogliad.ai.challenge.week3.cli.domain.service.InvariantRepository
import java.sql.ResultSet
import java.sql.Statement
import java.time.Instant

/**
 * Реализация [InvariantRepository] на SQLite.
 *
 * ## Архитектурная роль
 * - **Infrastructure Layer** — реализует port из domain-слоя
 * - **Hexagonal Architecture** — адаптер для persistence
 *
 * ## Схема БД
 * - Таблица `invariants`: id (INTEGER PRIMARY KEY AUTOINCREMENT), rule (TEXT NOT NULL), created_at (TEXT NOT NULL)
 *
 * ## Особенности
 * - Автоматическое создание схемы при инициализации
 * - Файл БД задаётся через конструктор (общий с другими репозиториями)
 * - Автоинкремент ID через SQLite INTEGER PRIMARY KEY
 *
 * @param database единый владелец SQLite JDBC-соединения
 */
class SqliteInvariantRepository(
    private val database: SqliteDatabase
) : InvariantRepository {

    private val connection get() = database.connection


    init {
        initializeSchema()
    }

    /**
     * Создаёт таблицу инвариантов, если она не существует.
     */
    private fun initializeSchema() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS invariants (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    rule TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    override suspend fun save(invariant: Invariant): Invariant {
        connection.autoCommit = false
        try {
            val sql = """
                INSERT INTO invariants (rule, created_at)
                VALUES (?, ?)
            """.trimIndent()

            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
                stmt.setString(1, invariant.rule)
                stmt.setString(2, invariant.createdAt.toString())
                stmt.executeUpdate()

                val generatedKeys = stmt.generatedKeys
                val id = if (generatedKeys.next()) {
                    generatedKeys.getInt(1)
                } else {
                    throw IllegalStateException("Failed to retrieve generated ID for invariant")
                }

                connection.commit()
                return invariant.copy(id = InvariantId(id))
            }
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override suspend fun findById(id: InvariantId): Invariant? {
        val sql = """
            SELECT id, rule, created_at
            FROM invariants
            WHERE id = ?
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, id.value)
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapRowToInvariant(rs) else null
            }
        }
    }

    override suspend fun findAll(): List<Invariant> {
        val sql = """
            SELECT id, rule, created_at
            FROM invariants
            ORDER BY id ASC
        """.trimIndent()

        return connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                val invariants = mutableListOf<Invariant>()
                while (rs.next()) {
                    invariants.add(mapRowToInvariant(rs))
                }
                invariants
            }
        }
    }

    override suspend fun delete(id: InvariantId): Boolean {
        connection.autoCommit = false
        try {
            val sql = "DELETE FROM invariants WHERE id = ?"
            val deleted = connection.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, id.value)
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
        val sql = "SELECT COUNT(*) FROM invariants"
        return connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }
    }

    /**
     * Маппит строку ResultSet в доменную модель Invariant.
     */
    private fun mapRowToInvariant(rs: ResultSet): Invariant {
        return Invariant(
            id = InvariantId(rs.getInt("id")),
            rule = rs.getString("rule"),
            createdAt = Instant.parse(rs.getString("created_at"))
        )
    }


}

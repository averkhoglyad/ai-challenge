package io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence

import io.averkhogliad.ai.challenge.week4.cli.domain.model.Profile
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ProfileId
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ProfileRepository
import java.sql.ResultSet
import java.time.Instant

/**
 * Реализация [ProfileRepository] на SQLite.
 *
 * ## Архитектурная роль
 * - **Infrastructure Layer** — реализует port из domain-слоя
 * - **Hexagonal Architecture** — адаптер для persistence
 *
 * ## Схема БД
 * - Таблица `profiles`: id, name, description, instructions, is_active, created_at, updated_at
 *
 * ## Особенности
 * - Автоматическое создание схемы при инициализации
 * - Файл БД задаётся через конструктор (общий с другими репозиториями)
 * - Поддержка транзакций для атомарности операций
 *
 * @param database единый владелец SQLite JDBC-соединения
 */
class SqliteProfileRepository(
    private val database: SqliteDatabase
) : ProfileRepository {

    private val connection get() = database.connection


    init {
        initializeSchema()
    }

    /**
     * Создаёт таблицу профилей, если она не существует.
     * Проверяет схему таблицы и пересоздаёт её при несовпадении.
     */
    private fun initializeSchema() {
        if (!hasCorrectSchema()) {
            connection.createStatement().use { stmt ->
                stmt.execute("DROP TABLE IF EXISTS profiles")
            }
        }

        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS profiles (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    description TEXT NOT NULL DEFAULT '',
                    instructions TEXT NOT NULL DEFAULT '',
                    is_active INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    /**
     * Проверяет, имеет ли таблица profiles правильную схему с колонками description и instructions.
     */
    private fun hasCorrectSchema(): Boolean {
        return try {
            connection.createStatement().use { stmt ->
                val rs = stmt.executeQuery("PRAGMA table_info(profiles)")
                val columns = mutableSetOf<String>()
                while (rs.next()) {
                    columns.add(rs.getString("name"))
                }
                columns.contains("description") && columns.contains("instructions")
            }
        } catch (_: Exception) {
            // Таблица не существует или другая ошибка
            false
        }
    }

    override suspend fun save(profile: Profile): Profile {
        connection.autoCommit = false
        try {
            val sql = """
                INSERT OR REPLACE INTO profiles (id, name, description, instructions, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, profile.id.value)
                stmt.setString(2, profile.name)
                stmt.setString(3, profile.description)
                stmt.setString(4, profile.instructions)
                stmt.setInt(5, if (profile.isActive) 1 else 0)
                stmt.setString(6, profile.createdAt.toString())
                stmt.setString(7, profile.updatedAt.toString())
                stmt.executeUpdate()
            }

            connection.commit()
            return profile
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override suspend fun findById(id: ProfileId): Profile? {
        val sql = """
            SELECT id, name, description, instructions, is_active, created_at, updated_at
            FROM profiles
            WHERE id = ?
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id.value)
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapRowToProfile(rs) else null
            }
        }
    }

    override suspend fun findByName(name: String): Profile? {
        val sql = """
            SELECT id, name, description, instructions, is_active, created_at, updated_at
            FROM profiles
            WHERE name = ?
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, name)
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapRowToProfile(rs) else null
            }
        }
    }

    override suspend fun findAll(): List<Profile> {
        val sql = """
            SELECT id, name, description, instructions, is_active, created_at, updated_at
            FROM profiles
            ORDER BY created_at ASC
        """.trimIndent()

        return connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                val profiles = mutableListOf<Profile>()
                while (rs.next()) {
                    profiles.add(mapRowToProfile(rs))
                }
                profiles
            }
        }
    }

    override suspend fun findActive(): Profile? {
        val sql = """
            SELECT id, name, description, instructions, is_active, created_at, updated_at
            FROM profiles
            WHERE is_active = 1
            LIMIT 1
        """.trimIndent()

        return connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                if (rs.next()) mapRowToProfile(rs) else null
            }
        }
    }

    override suspend fun delete(id: ProfileId) {
        connection.autoCommit = false
        try {
            val sql = "DELETE FROM profiles WHERE id = ?"
            connection.prepareStatement(sql).use { stmt ->
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

    override suspend fun existsByName(name: String): Boolean {
        val sql = "SELECT 1 FROM profiles WHERE name = ?"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, name)
            stmt.executeQuery().use { rs -> rs.next() }
        }
    }

    override suspend fun clearActive() {
        connection.autoCommit = false
        try {
            val sql = "UPDATE profiles SET is_active = 0, updated_at = ? WHERE is_active = 1"
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, Instant.now().toString())
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
     * Маппит строку ResultSet в доменную модель Profile.
     */
    private fun mapRowToProfile(rs: ResultSet): Profile {
        return Profile(
            id = ProfileId(rs.getString("id")),
            name = rs.getString("name"),
            description = rs.getString("description") ?: "",
            instructions = rs.getString("instructions") ?: "",
            isActive = rs.getInt("is_active") == 1,
            createdAt = Instant.parse(rs.getString("created_at")),
            updatedAt = Instant.parse(rs.getString("updated_at"))
        )
    }


}

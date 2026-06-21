package io.averkhogliad.ai.challenge.week2.infrastructure.persistence

import io.averkhogliad.ai.challenge.week2.domain.model.Task
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStatus
import io.averkhogliad.ai.challenge.week2.domain.service.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

/**
 * Реализация [TaskRepository] на SQLite через JDBC.
 *
 * ## Архитектурная роль
 * - **Infrastructure Layer** — реализует port из domain-слоя
 * - **Hexagonal Architecture** — адаптер для persistence
 * - **Suspend функции** — блокирующие JDBC операции выполняются в IO dispatcher
 *
 * ## Схема БД
 * - Таблица `tasks`: id, title, status, created_at, updated_at
 *
 * ## Особенности
 * - Автоматическое создание схемы при инициализации
 * - Файл БД создаётся в директории ~/ai-challenge/week2.db
 * - Поддержка транзакций для атомарности операций
 * - Автоматическое создание директории, если она не существует
 *
 * @param dbPath путь к файлу базы данных (по умолчанию ~/ai-challenge/week2.db)
 */
class SqliteTaskRepository(
    private val dbPath: String = defaultDbPath()
) : TaskRepository {

    companion object {
        /**
         * Возвращает путь к БД по умолчанию: ~/ai-challenge/week2.db
         */
        fun defaultDbPath(): String {
            val home = System.getProperty("user.home")
            return "$home/.ai-challenge/week2.db"
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
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS tasks (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    status TEXT NOT NULL CHECK(status IN ('OPEN', 'CLOSED', 'CANCELLED')),
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
            """.trimIndent()
            )
        }
    }

    override suspend fun save(task: Task): Unit = withContext(Dispatchers.IO) {
        val sql = """
            INSERT OR REPLACE INTO tasks (id, title, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, task.id.value)
            stmt.setString(2, task.title)
            stmt.setString(3, task.status.name)
            stmt.setString(4, task.createdAt.toString())
            stmt.setString(5, task.updatedAt.toString())
            stmt.executeUpdate()
        }
    }

    override suspend fun findById(id: TaskId): Task? = withContext(Dispatchers.IO) {
        val sql = "SELECT id, title, status, created_at, updated_at FROM tasks WHERE id = ?"

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id.value)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    mapRowToTask(rs)
                } else {
                    null
                }
            }
        }
    }

    override suspend fun findAll(): List<Task> = withContext(Dispatchers.IO) {
        val sql = "SELECT id, title, status, created_at, updated_at FROM tasks ORDER BY created_at DESC"

        connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                val tasks = mutableListOf<Task>()
                while (rs.next()) {
                    tasks.add(mapRowToTask(rs))
                }
                tasks
            }
        }
    }

    override suspend fun delete(id: TaskId): Unit = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM tasks WHERE id = ?"

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id.value)
            stmt.executeUpdate()
        }
    }

    override suspend fun exists(id: TaskId): Boolean = withContext(Dispatchers.IO) {
        val sql = "SELECT COUNT(*) FROM tasks WHERE id = ?"

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id.value)
            stmt.executeQuery().use { rs ->
                rs.next() && rs.getInt(1) > 0
            }
        }
    }

    /**
     * Маппит строку ResultSet в доменную модель Task.
     */
    private fun mapRowToTask(rs: java.sql.ResultSet): Task {
        return Task(
            id = TaskId(rs.getString("id")),
            title = rs.getString("title"),
            status = TaskStatus.valueOf(rs.getString("status")),
            createdAt = Instant.parse(rs.getString("created_at")),
            updatedAt = Instant.parse(rs.getString("updated_at"))
        )
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

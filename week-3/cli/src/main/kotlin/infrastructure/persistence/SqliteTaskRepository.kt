package io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence

import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.util.*

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
 * - Файл БД создаётся в директории ~/ai-challenge/week3.db
 * - Поддержка транзакций для атомарности операций
 * - Автоматическое создание директории, если она не существует
 *
 * @param database единый владелец SQLite JDBC-соединения
 */
class SqliteTaskRepository(
    private val database: SqliteDatabase = SqliteDatabase()
) : TaskRepository {

    private val connection get() = database.connection

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
                    updated_at TEXT NOT NULL,
                    event_id TEXT,
                    due_date TEXT
                )
            """.trimIndent()
            )

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS task_steps (
                    id TEXT PRIMARY KEY,
                    task_id TEXT NOT NULL,
                    text TEXT NOT NULL,
                    is_completed INTEGER NOT NULL DEFAULT 0,
                    step_order INTEGER NOT NULL,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
                )
            """.trimIndent()
            )
        }
    }

    override suspend fun save(task: Task): Unit = withContext(Dispatchers.IO) {
        val sql = """
            INSERT OR REPLACE INTO tasks (id, title, status, created_at, updated_at, event_id, due_date)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, task.id.value)
            stmt.setString(2, task.title)
            stmt.setString(3, task.status.name)
            stmt.setString(4, task.createdAt.toString())
            stmt.setString(5, task.updatedAt.toString())
            if (task.eventId != null) {
                stmt.setString(6, task.eventId.toString())
            } else {
                stmt.setNull(6, java.sql.Types.VARCHAR)
            }
            if (task.dueDate != null) {
                stmt.setString(7, task.dueDate.toString())
            } else {
                stmt.setNull(7, java.sql.Types.VARCHAR)
            }
            stmt.executeUpdate()
        }
    }

    override suspend fun findById(id: TaskId): Task? = withContext(Dispatchers.IO) {
        val sql = "SELECT id, title, status, created_at, updated_at, event_id, due_date FROM tasks WHERE id = ?"

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
        val sql =
            "SELECT id, title, status, created_at, updated_at, event_id, due_date FROM tasks ORDER BY created_at DESC"

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

    override suspend fun saveSteps(taskId: TaskId, steps: List<TaskStep>): Unit = withContext(Dispatchers.IO) {
        // Сначала удаляем старые шаги
        val deleteSql = "DELETE FROM task_steps WHERE task_id = ?"
        connection.prepareStatement(deleteSql).use { stmt ->
            stmt.setString(1, taskId.value)
            stmt.executeUpdate()
        }

        // Затем вставляем новые шаги
        val insertSql = """
            INSERT INTO task_steps (id, task_id, text, is_completed, step_order, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(insertSql).use { stmt ->
            for (step in steps) {
                stmt.setString(1, step.id.value)
                stmt.setString(2, step.taskId.value)
                stmt.setString(3, step.text)
                stmt.setInt(4, if (step.isCompleted) 1 else 0)
                stmt.setInt(5, step.order)
                stmt.setString(6, step.createdAt.toString())
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    override suspend fun findStepsByTaskId(taskId: TaskId): List<TaskStep> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT id, task_id, text, is_completed, step_order, created_at 
            FROM task_steps 
            WHERE task_id = ? 
            ORDER BY step_order ASC
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, taskId.value)
            stmt.executeQuery().use { rs ->
                val steps = mutableListOf<TaskStep>()
                while (rs.next()) {
                    steps.add(mapRowToTaskStep(rs))
                }
                steps
            }
        }
    }

    /**
     * Маппит строку ResultSet в доменную модель Task.
     */
    private fun mapRowToTask(rs: java.sql.ResultSet): Task {
        val eventIdStr = rs.getString("event_id")
        val dueDateStr = rs.getString("due_date")
        return Task(
            id = TaskId(rs.getString("id")),
            title = rs.getString("title"),
            status = TaskStatus.valueOf(rs.getString("status")),
            createdAt = Instant.parse(rs.getString("created_at")),
            updatedAt = Instant.parse(rs.getString("updated_at")),
            eventId = if (eventIdStr != null) UUID.fromString(eventIdStr) else null,
            dueDate = if (dueDateStr != null) LocalDate.parse(dueDateStr) else null
        )
    }

    /**
     * Маппит строку ResultSet в доменную модель TaskStep.
     */
    private fun mapRowToTaskStep(rs: java.sql.ResultSet): TaskStep {
        return TaskStep(
            id = TaskStepId(rs.getString("id")),
            taskId = TaskId(rs.getString("task_id")),
            text = rs.getString("text"),
            isCompleted = rs.getInt("is_completed") == 1,
            order = rs.getInt("step_order"),
            createdAt = Instant.parse(rs.getString("created_at"))
        )
    }

    override suspend fun updateEvent(taskId: TaskId, eventId: UUID, dueDate: LocalDate): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sql = "UPDATE tasks SET event_id = ?, due_date = ?, updated_at = ? WHERE id = ?"
                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, eventId.toString())
                    stmt.setString(2, dueDate.toString())
                    stmt.setString(3, Instant.now().toString())
                    stmt.setString(4, taskId.value)
                    stmt.executeUpdate()
                    Unit
                }
            }
        }

    override suspend fun clearEvent(taskId: TaskId): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sql = "UPDATE tasks SET event_id = NULL, due_date = NULL, updated_at = ? WHERE id = ?"
                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, Instant.now().toString())
                    stmt.setString(2, taskId.value)
                    stmt.executeUpdate()
                    Unit
                }
            }
        }

}

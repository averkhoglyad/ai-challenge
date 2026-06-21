package io.averkhogliad.ai.challenge.week2.infrastructure.persistence

import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStepId
import io.averkhogliad.ai.challenge.week2.domain.service.TaskStepRepository
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant

/**
 * Реализация [TaskStepRepository] на SQLite через JDBC.
 *
 * ## Архитектурная роль
 * - **Infrastructure Layer** — реализует port из domain-слоя
 * - **Hexagonal Architecture** — адаптер для persistence
 *
 * ## Схема БД
 * - Таблица `task_steps`: id, task_id, text, is_completed, step_order, created_at
 * - Foreign key на tasks(id) для каскадного удаления
 *
 * ## Особенности
 * - Автоматическое создание схемы при инициализации
 * - Файл БД задаётся через конструктор (общий с другими репозиториями)
 * - Поддержка транзакций для атомарности операций (save, delete, deleteByTaskId)
 * - Автоматическое создание директории, если она не существует
 *
 * @param dbPath путь к файлу базы данных
 */
class SqliteTaskStepRepository(
    private val dbPath: String
) : TaskStepRepository {

    private val connection: Connection by lazy {
        // Создаём директорию, если она не существует
        val parentDir = java.io.File(dbPath).parentFile
        if (parentDir != null && !parentDir.exists()) {
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
        createTable(connection)
    }

    companion object {
        /**
         * Создаёт таблицу task_steps, если она не существует.
         *
         * Может вызываться как извне для централизованной инициализации схемы,
         * так и автоматически при создании экземпляра репозитория.
         *
         * @param connection JDBC-соединение с SQLite
         */
        fun createTable(connection: Connection) {
            connection.createStatement().use { stmt ->
                // Таблица шагов задач
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS task_steps (
                        id TEXT PRIMARY KEY,
                        task_id TEXT NOT NULL,
                        text TEXT NOT NULL,
                        is_completed INTEGER NOT NULL DEFAULT 0,
                        step_order INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        FOREIGN KEY (task_id) REFERENCES tasks(id)
                    )
                    """.trimIndent()
                )

                // Индекс для быстрого поиска шагов по task_id
                stmt.execute(
                    """
                    CREATE INDEX IF NOT EXISTS idx_task_steps_task_id 
                    ON task_steps(task_id)
                    """.trimIndent()
                )
            }
        }
    }

    override fun save(step: TaskStep): TaskStep {
        connection.autoCommit = false
        try {
            // Upsert шага задачи
            val sql = """
                INSERT OR REPLACE INTO task_steps 
                (id, task_id, text, is_completed, step_order, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, step.id.value)
                stmt.setString(2, step.taskId.value)
                stmt.setString(3, step.text)
                stmt.setInt(4, if (step.isCompleted) 1 else 0)
                stmt.setInt(5, step.order)
                stmt.setString(6, step.createdAt.toString())
                stmt.executeUpdate()
            }

            connection.commit()
            return step
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override fun findByTaskId(taskId: TaskId): List<TaskStep> {
        val sql = """
            SELECT id, task_id, text, is_completed, step_order, created_at
            FROM task_steps
            WHERE task_id = ?
            ORDER BY step_order ASC
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
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

    override fun findById(stepId: TaskStepId): TaskStep? {
        val sql = """
            SELECT id, task_id, text, is_completed, step_order, created_at
            FROM task_steps
            WHERE id = ?
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, stepId.value)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    mapRowToTaskStep(rs)
                } else {
                    null
                }
            }
        }
    }

    override fun delete(stepId: TaskStepId): Boolean {
        connection.autoCommit = false
        try {
            val sql = "DELETE FROM task_steps WHERE id = ?"
            val deleted = connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, stepId.value)
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

    override fun deleteByTaskId(taskId: TaskId): Int {
        connection.autoCommit = false
        try {
            val sql = "DELETE FROM task_steps WHERE task_id = ?"
            val deleted = connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, taskId.value)
                stmt.executeUpdate()
            }
            connection.commit()
            return deleted
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override fun countByTaskId(taskId: TaskId): Int {
        val sql = "SELECT COUNT(*) FROM task_steps WHERE task_id = ?"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, taskId.value)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }
    }

    /**
     * Маппит строку ResultSet в доменную модель TaskStep.
     */
    private fun mapRowToTaskStep(rs: ResultSet): TaskStep {
        return TaskStep(
            id = TaskStepId(rs.getString("id")),
            taskId = TaskId(rs.getString("task_id")),
            text = rs.getString("text"),
            isCompleted = rs.getInt("is_completed") == 1,
            order = rs.getInt("step_order"),
            createdAt = Instant.parse(rs.getString("created_at"))
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

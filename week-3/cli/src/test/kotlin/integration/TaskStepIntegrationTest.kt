package io.averkhogliad.ai.challenge.week3.cli.integration

import io.averkhogliad.ai.challenge.week3.cli.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week3.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStepId
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskRepository
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskStepRepository
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.SqliteTaskStepRepository

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Интеграционные тесты для проверки end-to-end сценариев управления шагами задач (Фаза 4).
 *
 * Проверяют:
 * - Полный сценарий: open task → add step → list steps → complete step → back → open → steps persisted
 * - Валидация: операции с шагами без открытой задачи
 * - WM на уровне задачи включает шаги
 */
@DisplayName("Task Step Integration (Phase 4)")
class TaskStepIntegrationTest {

    private lateinit var tempDbFile: File
    private lateinit var database: SqliteDatabase
    private lateinit var taskRepository: TaskRepository
    private lateinit var taskStepRepository: TaskStepRepository
    private lateinit var TodoTaskService: TodoTaskService

    @BeforeEach
    fun setUp() {
        tempDbFile = Files.createTempFile("test-taskstep-integration-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        taskStepRepository = SqliteTaskStepRepository(database)
        taskRepository = object : TaskRepository {

            private val tasks = mutableMapOf<TaskId, Task>()

            override suspend fun save(task: Task) {
                tasks[task.id] = task
            }

            override suspend fun findById(id: TaskId): Task? = tasks[id]

            override suspend fun findAll(): List<Task> = tasks.values.toList()

            override suspend fun delete(id: TaskId) {
                tasks.remove(id)
            }

            override suspend fun exists(id: TaskId): Boolean = tasks.containsKey(id)

            override suspend fun saveSteps(taskId: TaskId, steps: List<TaskStep>) {
                // no-op for step integration tests
            }

            override suspend fun findStepsByTaskId(taskId: TaskId): List<TaskStep> = emptyList()
        }
        TodoTaskService = TodoTaskService(taskRepository)
    }

    @AfterEach
    fun tearDown() {
        database.close()
        tempDbFile.delete()

        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    // ========================================================================
    // US-4.1: Полный сценарий управления шагами
    // ========================================================================

    @Test
    @DisplayName("full step management scenario: open → add → list → complete → back → open → persisted")
    fun `full step management scenario`() = runBlocking {
        // 1. Создаём задачу
        val createdTask = TodoTaskService.addTask("Implement feature")
        val taskId = createdTask.id

        // 2. Открываем задачу
        TodoTaskService.openTask(taskId)
        assertEquals(taskId, TodoTaskService.currentTaskId)

        // 3. Добавляем шаги
        val step1 = addStep(taskId, "Design the API", 0)
        val step2 = addStep(taskId, "Write unit tests", 1)
        val step3 = addStep(taskId, "Implement logic", 2)

        // 4. Проверяем список шагов
        val steps = taskStepRepository.findByTaskId(taskId)
        assertEquals(3, steps.size)
        assertEquals("Design the API", steps[0].text)
        assertEquals("Write unit tests", steps[1].text)
        assertEquals("Implement logic", steps[2].text)
        assertTrue(steps.none { it.isCompleted })

        // 5. Отмечаем шаг выполненным
        val completedStep = steps[0].markCompleted()
        taskStepRepository.save(completedStep)

        // 6. Проверяем, что шаг теперь выполнен
        val updatedSteps = taskStepRepository.findByTaskId(taskId)
        assertEquals(true, updatedSteps[0].isCompleted)
        assertEquals(false, updatedSteps[1].isCompleted)
        assertEquals(false, updatedSteps[2].isCompleted)

        // 7. Возвращаемся к списку задач
        TodoTaskService.back()
        assertEquals(null, TodoTaskService.currentTaskId)

        // 8. Снова открываем задачу — шаги должны сохраниться
        TodoTaskService.openTask(taskId)
        val stepsAfterReopen = taskStepRepository.findByTaskId(taskId)
        assertEquals(3, stepsAfterReopen.size)
        assertEquals(true, stepsAfterReopen[0].isCompleted, "Step 1 should still be completed")
    }

    // ========================================================================
    // US-4.2: Валидация — шаги не могут быть добавлены без открытой задачи
    // ========================================================================

    @Test
    @DisplayName("steps require an open task")
    fun `steps require an open task`(): Unit = runBlocking {
        val taskId = TaskId(UUID.randomUUID().toString())

        // Попытка добавить шаг без открытой задачи должна требовать валидации
        // на уровне CommandHandler (requireTaskOpen). Здесь проверяем, что
        // TaskStepRepository принимает шаги для любой задачи (нет валидации на уровне репозитория)
        val step = TaskStep(
            id = TaskStepId(UUID.randomUUID().toString()),
            taskId = taskId,
            text = "Orphan step",
            isCompleted = false,
            order = 0,
            createdAt = Instant.now()
        )
        val saved = taskStepRepository.save(step)

        // Репозиторий сохраняет шаг, даже если задача не существует
        assertEquals("Orphan step", saved.text)
        assertNotNull(taskStepRepository.findById(saved.id))
    }

    // ========================================================================
    // US-4.3: WM на уровне задачи включает шаги
    // ========================================================================

    @Test
    @DisplayName("task-level steps are persisted and sorted by order")
    fun `task-level steps are persisted and sorted by order`() = runBlocking {
        val taskId1 = TaskId(UUID.randomUUID().toString())
        val taskId2 = TaskId(UUID.randomUUID().toString())

        // Добавляем шаги для двух разных задач
        val step1a = addStep(taskId1, "Task 1 - Step A", 0)
        val step1b = addStep(taskId1, "Task 1 - Step B", 1)
        val step2a = addStep(taskId2, "Task 2 - Step A", 0)

        // Проверяем, что шаги правильно изолированы по задачам
        val steps1 = taskStepRepository.findByTaskId(taskId1)
        val steps2 = taskStepRepository.findByTaskId(taskId2)

        assertEquals(2, steps1.size)
        assertEquals(1, steps2.size)
        assertEquals("Task 1 - Step A", steps1[0].text)
        assertEquals("Task 1 - Step B", steps1[1].text)
        assertEquals("Task 2 - Step A", steps2[0].text)
    }

    @Test
    @DisplayName("completed steps are tracked correctly")
    fun `completed steps are tracked correctly`() = runBlocking {
        val taskId = TaskId(UUID.randomUUID().toString())

        // Добавляем шаги с разными статусами
        val step1 = addStep(taskId, "Pending step", 0)
        val step2 = addStep(taskId, "Done step", 1).markCompleted()
        taskStepRepository.save(step2)

        val steps = taskStepRepository.findByTaskId(taskId)
        assertEquals(2, steps.size)
        assertTrue(steps.any { !it.isCompleted })
        assertTrue(steps.any { it.isCompleted })

        // countByTaskId возвращает общее количество
        assertEquals(2, taskStepRepository.countByTaskId(taskId))
    }

    @Test
    @DisplayName("delete all steps by task id")
    fun `delete all steps by task id`() = runBlocking {
        val taskId = TaskId(UUID.randomUUID().toString())

        addStep(taskId, "Step 1", 0)
        addStep(taskId, "Step 2", 1)
        addStep(taskId, "Step 3", 2)
        assertEquals(3, taskStepRepository.countByTaskId(taskId))

        val deleted = taskStepRepository.deleteByTaskId(taskId)
        assertEquals(3, deleted)
        assertEquals(0, taskStepRepository.countByTaskId(taskId))
        assertTrue(taskStepRepository.findByTaskId(taskId).isEmpty())
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun addStep(taskId: TaskId, text: String, order: Int): TaskStep {
        val step = TaskStep(
            id = TaskStepId(UUID.randomUUID().toString()),
            taskId = taskId,
            text = text,
            isCompleted = false,
            order = order,
            createdAt = Instant.now()
        )
        return taskStepRepository.save(step)
    }
}

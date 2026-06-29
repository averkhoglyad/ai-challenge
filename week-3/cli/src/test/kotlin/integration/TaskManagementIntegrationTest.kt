package io.averkhogliad.ai.challenge.week3.cli.integration

import io.averkhogliad.ai.challenge.week3.cli.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week3.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStatus
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration-тесты для проверки end-to-end сценариев управления задачами.
 *
 * Покрывают пользовательские истории US-2.1–US-2.9 и комплексный сценарий.
 */
class TaskManagementIntegrationTest {

    private lateinit var taskRepository: TaskRepository
    private lateinit var TodoTaskService: TodoTaskService

    @BeforeEach
    fun setup() {
        taskRepository = InMemoryTaskRepository()
        TodoTaskService = TodoTaskService(taskRepository)
    }

    // ========================================================================
    // US-2.1: Создание задачи
    // ========================================================================

    @Test
    fun `create task via add command`() = runBlocking {
        // Дано: пустой репозиторий
        assertTrue(taskRepository.findAll().isEmpty())

        // Когда: выполняем :add "Купить молоко"
        val createdTask = TodoTaskService.addTask("Купить молоко")

        // Тогда: задача создана, статус OPEN, можно найти по ID
        assertNotNull(createdTask.id)
        assertEquals("Купить молоко", createdTask.title)
        assertEquals(TaskStatus.OPEN, createdTask.status)

        val foundTask = taskRepository.findById(createdTask.id)
        assertNotNull(foundTask)
        assertEquals(createdTask.id, foundTask.id)
        assertEquals("Купить молоко", foundTask.title)
    }

    // ========================================================================
    // US-2.2: Просмотр списка задач
    // ========================================================================

    @Test
    fun `list all tasks`() = runBlocking {
        // Дано: 3 задачи с разными статусами
        val task1 = TodoTaskService.addTask("Задача 1")
        val task2 = TodoTaskService.addTask("Задача 2")
        val task3 = TodoTaskService.addTask("Задача 3")

        // Закрываем вторую задачу
        TodoTaskService.openTask(task2.id)
        TodoTaskService.closeTask(null)

        // Отменяем третью задачу
        TodoTaskService.cancelTask(task3.id)

        // Когда: выполняем :list
        val allTasks = TodoTaskService.listTasks()

        // Тогда: все задачи отображаются
        assertEquals(3, allTasks.size)
        assertTrue(allTasks.any { it.id == task1.id && it.status == TaskStatus.OPEN })
        assertTrue(allTasks.any { it.id == task2.id && it.status == TaskStatus.CLOSED })
        assertTrue(allTasks.any { it.id == task3.id && it.status == TaskStatus.CANCELLED })
    }

    // ========================================================================
    // US-2.3: Редактирование задачи
    // ========================================================================

    @Test
    fun `edit task by id`() = runBlocking {
        // Дано: задача с ID "abc123"
        val createdTask = TodoTaskService.addTask("Старое название")
        val taskId = createdTask.id

        // Когда: выполняем :edit abc123 "Новое название"
        val updatedTask = TodoTaskService.editTask(taskId, "Новое название")

        // Тогда: заголовок задачи обновлен
        assertEquals("Новое название", updatedTask.title)
        assertEquals(taskId, updatedTask.id)

        val foundTask = taskRepository.findById(taskId)
        assertNotNull(foundTask)
        assertEquals("Новое название", foundTask.title)
    }

    // ========================================================================
    // US-2.4: Удаление задачи
    // ========================================================================

    @Test
    fun `drop task by id`() = runBlocking {
        // Дано: задача с ID "abc123"
        val createdTask = TodoTaskService.addTask("Задача для удаления")
        val taskId = createdTask.id

        assertTrue(taskRepository.exists(taskId))

        // Когда: выполняем :drop abc123
        TodoTaskService.dropTask(taskId)

        // Тогда: задача удалена из репозитория
        assertNull(taskRepository.findById(taskId))
        assertTrue(!taskRepository.exists(taskId))
    }

    // ========================================================================
    // US-2.5: Открытие задачи
    // ========================================================================

    @Test
    fun `open task sets currentTaskId`() = runBlocking {
        // Дано: задача с ID "abc123"
        val createdTask = TodoTaskService.addTask("Задача для открытия")
        val taskId = createdTask.id

        assertNull(TodoTaskService.currentTaskId)

        // Когда: выполняем :open abc123
        val openedTask = TodoTaskService.openTask(taskId)

        // Тогда: currentTaskId установлен в "abc123"
        assertEquals(taskId, TodoTaskService.currentTaskId)
        assertEquals(taskId, openedTask.id)
    }

    // ========================================================================
    // US-2.6: Закрытие задачи
    // ========================================================================

    @Test
    fun `close task changes status to CLOSED`() = runBlocking {
        // Дано: открытая задача с ID "abc123"
        val createdTask = TodoTaskService.addTask("Задача для закрытия")
        val taskId = createdTask.id
        TodoTaskService.openTask(taskId)

        assertEquals(TaskStatus.OPEN, createdTask.status)
        assertEquals(taskId, TodoTaskService.currentTaskId)

        // Когда: выполняем :close
        val closedTask = TodoTaskService.closeTask(null)

        // Тогда: статус задачи изменен на CLOSED, currentTaskId очищен
        assertEquals(TaskStatus.CLOSED, closedTask.status)
        assertNull(TodoTaskService.currentTaskId)

        val foundTask = taskRepository.findById(taskId)
        assertNotNull(foundTask)
        assertEquals(TaskStatus.CLOSED, foundTask.status)
    }

    // ========================================================================
    // US-2.7: Отмена задачи
    // ========================================================================

    @Test
    fun `cancel task changes status to CANCELLED`() = runBlocking {
        // Дано: задача с ID "abc123"
        val createdTask = TodoTaskService.addTask("Задача для отмены")
        val taskId = createdTask.id

        assertEquals(TaskStatus.OPEN, createdTask.status)

        // Когда: выполняем :cancel abc123
        val cancelledTask = TodoTaskService.cancelTask(taskId)

        // Тогда: статус задачи изменен на CANCELLED
        assertEquals(TaskStatus.CANCELLED, cancelledTask.status)

        val foundTask = taskRepository.findById(taskId)
        assertNotNull(foundTask)
        assertEquals(TaskStatus.CANCELLED, foundTask.status)
    }

    // ========================================================================
    // US-2.8: Возврат к списку
    // ========================================================================

    @Test
    fun `back command clears currentTaskId`() = runBlocking {
        // Дано: открытая задача
        val createdTask = TodoTaskService.addTask("Задача для возврата")
        val taskId = createdTask.id
        TodoTaskService.openTask(taskId)

        assertEquals(taskId, TodoTaskService.currentTaskId)

        // Когда: выполняем :back
        TodoTaskService.back()

        // Тогда: currentTaskId очищен
        assertNull(TodoTaskService.currentTaskId)
    }

    // ========================================================================
    // US-2.9: Контекстные команды
    // ========================================================================

    @Test
    fun `contextual edit without id uses currentTaskId`() = runBlocking {
        // Дано: открытая задача с ID "abc123"
        val createdTask = TodoTaskService.addTask("Старое название")
        val taskId = createdTask.id
        TodoTaskService.openTask(taskId)

        assertEquals(taskId, TodoTaskService.currentTaskId)

        // Когда: выполняем :edit "Новое название" (без ID)
        val updatedTask = TodoTaskService.editTask(null, "Новое название")

        // Тогда: задача с ID "abc123" обновлена
        assertEquals("Новое название", updatedTask.title)
        assertEquals(taskId, updatedTask.id)

        val foundTask = taskRepository.findById(taskId)
        assertNotNull(foundTask)
        assertEquals("Новое название", foundTask.title)
    }

    @Test
    fun `contextual close without id uses currentTaskId`() = runBlocking {
        // Дано: открытая задача с ID "abc123"
        val createdTask = TodoTaskService.addTask("Задача для контекстного закрытия")
        val taskId = createdTask.id
        TodoTaskService.openTask(taskId)

        assertEquals(taskId, TodoTaskService.currentTaskId)
        assertEquals(TaskStatus.OPEN, createdTask.status)

        // Когда: выполняем :close (без ID)
        val closedTask = TodoTaskService.closeTask(null)

        // Тогда: задача с ID "abc123" закрыта
        assertEquals(TaskStatus.CLOSED, closedTask.status)
        assertEquals(taskId, closedTask.id)
        assertNull(TodoTaskService.currentTaskId)

        val foundTask = taskRepository.findById(taskId)
        assertNotNull(foundTask)
        assertEquals(TaskStatus.CLOSED, foundTask.status)
    }

    // ========================================================================
    // Комплексный сценарий: Full workflow
    // ========================================================================

    @Test
    fun `full workflow - create, open, edit, close, list`() = runBlocking {
        // 1. Создаем задачу: :add "Задача 1"
        val createdTask = TodoTaskService.addTask("Задача 1")
        val taskId = createdTask.id
        assertEquals(TaskStatus.OPEN, createdTask.status)

        // 2. Открываем задачу: :open <id>
        val openedTask = TodoTaskService.openTask(taskId)
        assertEquals(taskId, TodoTaskService.currentTaskId)
        assertEquals(taskId, openedTask.id)

        // 3. Редактируем контекстно: :edit "Обновленное название"
        val updatedTask = TodoTaskService.editTask(null, "Обновленное название")
        assertEquals("Обновленное название", updatedTask.title)
        assertEquals(taskId, updatedTask.id)

        // 4. Закрываем контекстно: :close
        val closedTask = TodoTaskService.closeTask(null)
        assertEquals(TaskStatus.CLOSED, closedTask.status)
        assertEquals(taskId, closedTask.id)
        assertNull(TodoTaskService.currentTaskId)

        // 5. Проверяем список: :list
        val allTasks = TodoTaskService.listTasks()
        assertEquals(1, allTasks.size)

        // 6. Задача должна быть в статусе CLOSED
        val finalTask = allTasks.first()
        assertEquals(taskId, finalTask.id)
        assertEquals("Обновленное название", finalTask.title)
        assertEquals(TaskStatus.CLOSED, finalTask.status)
    }
}

// ============================================================================
// In-memory реализация TaskRepository для тестов
// ============================================================================

/**
 * In-memory реализация [TaskRepository] для интеграционного тестирования.
 *
 * Хранит задачи в [MutableMap], что позволяет быстро тестировать бизнес-логику
 * без необходимости поднимать реальную базу данных.
 */
class InMemoryTaskRepository : TaskRepository {
    private val tasks = mutableMapOf<TaskId, Task>()

    override suspend fun save(task: Task) {
        tasks[task.id] = task
    }

    override suspend fun findById(id: TaskId): Task? {
        return tasks[id]
    }

    override suspend fun findAll(): List<Task> {
        return tasks.values.toList()
    }

    override suspend fun delete(id: TaskId) {
        tasks.remove(id)
    }

    override suspend fun exists(id: TaskId): Boolean {
        return tasks.containsKey(id)
    }

    override suspend fun saveSteps(
        taskId: TaskId,
        steps: List<io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep>
    ) {
        // no-op for task management tests
    }

    override suspend fun findStepsByTaskId(taskId: TaskId): List<io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep> {
        return emptyList()
    }

    override suspend fun updateEvent(taskId: TaskId, eventId: UUID, dueDate: LocalDate): Result<Unit> =
        Result.success(Unit)

    override suspend fun clearEvent(taskId: TaskId): Result<Unit> =
        Result.success(Unit)
}

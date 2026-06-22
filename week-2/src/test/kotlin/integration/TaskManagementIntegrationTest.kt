package io.averkhogliad.ai.challenge.week2.integration

import io.averkhogliad.ai.challenge.week2.application.executor.TaskManagerExecutor
import io.averkhogliad.ai.challenge.week2.domain.model.Task
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStatus
import io.averkhogliad.ai.challenge.week2.domain.service.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    private lateinit var taskManagerExecutor: TaskManagerExecutor

    @BeforeEach
    fun setup() {
        taskRepository = InMemoryTaskRepository()
        taskManagerExecutor = TaskManagerExecutor(taskRepository)
    }

    // ========================================================================
    // US-2.1: Создание задачи
    // ========================================================================

    @Test
    fun `create task via add command`() = runBlocking {
        // Дано: пустой репозиторий
        assertTrue(taskRepository.findAll().isEmpty())

        // Когда: выполняем :add "Купить молоко"
        val createdTask = taskManagerExecutor.handleAddTask("Купить молоко")

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
        val task1 = taskManagerExecutor.handleAddTask("Задача 1")
        val task2 = taskManagerExecutor.handleAddTask("Задача 2")
        val task3 = taskManagerExecutor.handleAddTask("Задача 3")

        // Закрываем вторую задачу
        taskManagerExecutor.handleOpenTask(task2.id)
        taskManagerExecutor.handleCloseTask(null)

        // Отменяем третью задачу
        taskManagerExecutor.handleCancelTask(task3.id)

        // Когда: выполняем :list
        val allTasks = taskManagerExecutor.handleListTasks()

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
        val createdTask = taskManagerExecutor.handleAddTask("Старое название")
        val taskId = createdTask.id

        // Когда: выполняем :edit abc123 "Новое название"
        val updatedTask = taskManagerExecutor.handleEditTask(taskId, "Новое название")

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
        val createdTask = taskManagerExecutor.handleAddTask("Задача для удаления")
        val taskId = createdTask.id

        assertTrue(taskRepository.exists(taskId))

        // Когда: выполняем :drop abc123
        taskManagerExecutor.handleDropTask(taskId)

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
        val createdTask = taskManagerExecutor.handleAddTask("Задача для открытия")
        val taskId = createdTask.id

        assertNull(taskManagerExecutor.currentTaskId)

        // Когда: выполняем :open abc123
        val openedTask = taskManagerExecutor.handleOpenTask(taskId)

        // Тогда: currentTaskId установлен в "abc123"
        assertEquals(taskId, taskManagerExecutor.currentTaskId)
        assertEquals(taskId, openedTask.id)
    }

    // ========================================================================
    // US-2.6: Закрытие задачи
    // ========================================================================

    @Test
    fun `close task changes status to CLOSED`() = runBlocking {
        // Дано: открытая задача с ID "abc123"
        val createdTask = taskManagerExecutor.handleAddTask("Задача для закрытия")
        val taskId = createdTask.id
        taskManagerExecutor.handleOpenTask(taskId)

        assertEquals(TaskStatus.OPEN, createdTask.status)
        assertEquals(taskId, taskManagerExecutor.currentTaskId)

        // Когда: выполняем :close
        val closedTask = taskManagerExecutor.handleCloseTask(null)

        // Тогда: статус задачи изменен на CLOSED, currentTaskId очищен
        assertEquals(TaskStatus.CLOSED, closedTask.status)
        assertNull(taskManagerExecutor.currentTaskId)

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
        val createdTask = taskManagerExecutor.handleAddTask("Задача для отмены")
        val taskId = createdTask.id

        assertEquals(TaskStatus.OPEN, createdTask.status)

        // Когда: выполняем :cancel abc123
        val cancelledTask = taskManagerExecutor.handleCancelTask(taskId)

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
        val createdTask = taskManagerExecutor.handleAddTask("Задача для возврата")
        val taskId = createdTask.id
        taskManagerExecutor.handleOpenTask(taskId)

        assertEquals(taskId, taskManagerExecutor.currentTaskId)

        // Когда: выполняем :back
        taskManagerExecutor.handleBack()

        // Тогда: currentTaskId очищен
        assertNull(taskManagerExecutor.currentTaskId)
    }

    // ========================================================================
    // US-2.9: Контекстные команды
    // ========================================================================

    @Test
    fun `contextual edit without id uses currentTaskId`() = runBlocking {
        // Дано: открытая задача с ID "abc123"
        val createdTask = taskManagerExecutor.handleAddTask("Старое название")
        val taskId = createdTask.id
        taskManagerExecutor.handleOpenTask(taskId)

        assertEquals(taskId, taskManagerExecutor.currentTaskId)

        // Когда: выполняем :edit "Новое название" (без ID)
        val updatedTask = taskManagerExecutor.handleEditTask(null, "Новое название")

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
        val createdTask = taskManagerExecutor.handleAddTask("Задача для контекстного закрытия")
        val taskId = createdTask.id
        taskManagerExecutor.handleOpenTask(taskId)

        assertEquals(taskId, taskManagerExecutor.currentTaskId)
        assertEquals(TaskStatus.OPEN, createdTask.status)

        // Когда: выполняем :close (без ID)
        val closedTask = taskManagerExecutor.handleCloseTask(null)

        // Тогда: задача с ID "abc123" закрыта
        assertEquals(TaskStatus.CLOSED, closedTask.status)
        assertEquals(taskId, closedTask.id)
        assertNull(taskManagerExecutor.currentTaskId)

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
        val createdTask = taskManagerExecutor.handleAddTask("Задача 1")
        val taskId = createdTask.id
        assertEquals(TaskStatus.OPEN, createdTask.status)

        // 2. Открываем задачу: :open <id>
        val openedTask = taskManagerExecutor.handleOpenTask(taskId)
        assertEquals(taskId, taskManagerExecutor.currentTaskId)
        assertEquals(taskId, openedTask.id)

        // 3. Редактируем контекстно: :edit "Обновленное название"
        val updatedTask = taskManagerExecutor.handleEditTask(null, "Обновленное название")
        assertEquals("Обновленное название", updatedTask.title)
        assertEquals(taskId, updatedTask.id)

        // 4. Закрываем контекстно: :close
        val closedTask = taskManagerExecutor.handleCloseTask(null)
        assertEquals(TaskStatus.CLOSED, closedTask.status)
        assertEquals(taskId, closedTask.id)
        assertNull(taskManagerExecutor.currentTaskId)

        // 5. Проверяем список: :list
        val allTasks = taskManagerExecutor.handleListTasks()
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
        steps: List<io.averkhogliad.ai.challenge.week2.domain.model.TaskStep>
    ) {
        // no-op for task management tests
    }

    override suspend fun findStepsByTaskId(taskId: TaskId): List<io.averkhogliad.ai.challenge.week2.domain.model.TaskStep> {
        return emptyList()
    }
}

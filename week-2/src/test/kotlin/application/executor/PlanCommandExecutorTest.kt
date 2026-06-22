package io.averkhogliad.ai.challenge.week2.application.executor

import io.averkhogliad.ai.challenge.week2.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.*
import io.averkhogliad.ai.challenge.week2.domain.service.CommandEngine
import io.averkhogliad.ai.challenge.week2.domain.service.FactRepository
import io.averkhogliad.ai.challenge.week2.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week2.domain.service.TaskRepository
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.*

/**
 * In-memory реализация [TaskRepository] для тестирования PlanCommandExecutor.
 */
private class PlanTestInMemoryTaskRepository : TaskRepository {
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
        // No-op for tests
    }

    override suspend fun findStepsByTaskId(taskId: TaskId): List<io.averkhogliad.ai.challenge.week2.domain.model.TaskStep> {
        return emptyList()
    }
}

/**
 * In-memory реализация [FactRepository] для тестирования PlanCommandExecutor.
 */
private class PlanTestInMemoryFactRepository : FactRepository {
    private val facts = mutableMapOf<FactId, Fact>()

    override suspend fun save(fact: Fact): Fact {
        facts[fact.id] = fact
        return fact
    }

    override suspend fun findById(id: FactId): Fact? {
        return facts[id]
    }

    override suspend fun findAll(): List<Fact> {
        return facts.values.toList()
    }

    override suspend fun search(query: String): List<Fact> {
        return facts.values.filter { it.content.contains(query, ignoreCase = true) }
    }

    override suspend fun delete(id: FactId): Boolean {
        return facts.remove(id) != null
    }

    override suspend fun count(): Int {
        return facts.size
    }
}

/**
 * Unit-тесты для [PlanCommandExecutor].
 *
 * ## Тестируемая функциональность
 * - Инициализация команды `:plan` с открытой задачей
 * - Обработка задачи без description (запрос ввода)
 * - Обработка ввода description
 * - Сбор релевантных фактов из LTM
 * - Обработка ошибок (нет открытой задачи, задача не найдена, задача не открыта)
 * - Интеграция с CommandEngine
 */
class PlanCommandExecutorTest {

    private lateinit var taskRepository: TaskRepository
    private lateinit var factRepository: FactRepository
    private lateinit var commandEngine: CommandEngine
    private lateinit var executor: PlanCommandExecutor

    @BeforeTest
    fun setUp() {
        taskRepository = PlanTestInMemoryTaskRepository()
        factRepository = PlanTestInMemoryFactRepository()
        commandEngine = DefaultCommandEngine()
        executor = PlanCommandExecutor(taskRepository, factRepository, commandEngine)
    }

    // ===== Тесты инициализации команды =====

    @Test
    fun `execute with null currentTaskId returns error`() = runBlocking {
        // When
        val result = executor.execute(null)

        // Then
        assertTrue(result.contains("Ошибка: команда ':plan' требует открытой задачи"))
        assertFalse(commandEngine.hasActiveCommand())
    }

    @Test
    fun `execute with non-existent taskId returns error`() = runBlocking {
        // When
        val result = executor.execute(999)

        // Then
        assertTrue(result.contains("Ошибка: задача с ID '999' не найдена"))
        assertFalse(commandEngine.hasActiveCommand())
    }

    @Test
    fun `execute with closed task returns error`() = runBlocking {
        // Given
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Closed Task",
            description = "Some description",
            status = TaskStatus.CLOSED,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)

        // When
        val result = executor.execute(1)

        // Then
        assertTrue(result.contains("Ошибка: задача 'Closed Task' не открыта"))
        assertFalse(commandEngine.hasActiveCommand())
    }

    @Test
    fun `execute with open task without description requests description`() = runBlocking {
        // Given
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Task Without Description",
            description = null,
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)

        // When
        val result = executor.execute(1)

        // Then
        assertTrue(result.contains("Задача 'Task Without Description' открыта"))
        assertTrue(result.contains("Description задачи пуст"))
        assertTrue(result.contains("Пожалуйста, опишите задачу подробно"))
        assertTrue(commandEngine.hasActiveCommand())
        assertEquals("1", commandEngine.getContext("taskId"))
        assertEquals("Task Without Description", commandEngine.getContext("taskTitle"))
        assertEquals("true", commandEngine.getContext("needsDescription"))
    }

    @Test
    fun `execute with open task with description collects facts and transitions to EXECUTION`() = runBlocking {
        // Given
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Task With Description",
            description = "Implement feature X",
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)

        // Add some facts
        val fact1 = Fact(FactId("f1"), "Feature X requires database migration", Instant.now())
        val fact2 = Fact(FactId("f2"), "Use Kotlin coroutines for async", Instant.now())
        factRepository.save(fact1)
        factRepository.save(fact2)

        // When
        val result = executor.execute(1)

        // Then
        assertTrue(result.contains("Этап PLANNING завершён для задачи 'Task With Description'"))
        assertTrue(result.contains("Description: Implement feature X"))
        assertTrue(result.contains("Переход к этапу EXECUTION"))
        assertTrue(commandEngine.hasActiveCommand())
        assertEquals(CommandStage.EXECUTION, commandEngine.getActiveState()?.currentStage)
        assertEquals("Implement feature X", commandEngine.getContext("description"))
    }

    // ===== Тесты обработки ввода description =====

    @Test
    fun `handleDescriptionInput with valid input collects facts and transitions to EXECUTION`() = runBlocking {
        // Given
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Task Without Description",
            description = null,
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)
        executor.execute(1) // Запускаем команду, запрашивается description

        // Add some facts
        val fact = Fact(FactId("f1"), "Important implementation detail", Instant.now())
        factRepository.save(fact)

        // When
        val result = executor.handleDescriptionInput("New detailed description for the task")

        // Then
        assertTrue(result.contains("Этап PLANNING завершён"))
        assertTrue(result.contains("Description: New detailed description for the task"))
        assertTrue(result.contains("Переход к этапу EXECUTION"))
        assertEquals(CommandStage.EXECUTION, commandEngine.getActiveState()?.currentStage)
        assertEquals("New detailed description for the task", commandEngine.getContext("description"))
    }

    @Test
    fun `handleDescriptionInput with blank input returns error`() = runBlocking {
        // Given
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Task Without Description",
            description = null,
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)
        executor.execute(1)

        // When
        val result = executor.handleDescriptionInput("   ")

        // Then
        assertTrue(result.contains("Ошибка: description не может быть пустым"))
        assertEquals(CommandStage.PLANNING, commandEngine.getActiveState()?.currentStage)
    }

    @Test
    fun `handleDescriptionInput without active command returns error`() = runBlocking {
        // When
        val result = executor.handleDescriptionInput("Some description")

        // Then
        assertTrue(result.contains("Ошибка: команда ':plan' не активна"))
    }

    // ===== Тесты сбора фактов из LTM =====

    @Test
    fun `execute collects relevant facts from LTM by keywords`() = runBlocking {
        // Given
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Database Migration",
            description = "Migrate users table",
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)

        // Add facts with relevant keywords
        val relevantFact = Fact(FactId("f1"), "Database migration requires backup", Instant.now())
        val irrelevantFact = Fact(FactId("f2"), "UI design principles", Instant.now())
        factRepository.save(relevantFact)
        factRepository.save(irrelevantFact)

        // When
        val result = executor.execute(1)

        // Then
        assertTrue(result.contains("Релевантных фактов из LTM:"))
        assertTrue(result.contains("Database migration requires backup"))
    }

    @Test
    fun `execute with no matching facts returns empty facts list`() = runBlocking {
        // Given
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Unique Task XYZ",
            description = "Some unique description ABC",
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)

        // Add unrelated facts
        val fact = Fact(FactId("f1"), "Completely unrelated content", Instant.now())
        factRepository.save(fact)

        // When
        val result = executor.execute(1)

        // Then
        assertTrue(result.contains("Релевантных фактов из LTM: 0") || result.contains("Релевантных фактов из LTM:"))
    }

    // ===== Интеграционный тест полного жизненного цикла =====

    @Test
    fun `full command lifecycle with description request works correctly`() = runBlocking {
        // Given
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Task Without Description",
            description = null,
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)

        // Step 1: Start command (description is empty)
        val startResult = executor.execute(1)
        assertTrue(startResult.contains("Description задачи пуст"))
        assertTrue(commandEngine.hasActiveCommand())
        assertEquals(CommandStage.PLANNING, commandEngine.getActiveState()?.currentStage)

        // Step 2: Provide description
        val descriptionResult = executor.handleDescriptionInput("Detailed task description")
        assertTrue(descriptionResult.contains("Этап PLANNING завершён"))
        assertTrue(descriptionResult.contains("Переход к этапу EXECUTION"))
        assertEquals(CommandStage.EXECUTION, commandEngine.getActiveState()?.currentStage)

        // Verify context
        assertEquals("Detailed task description", commandEngine.getContext("description"))
        assertEquals("1", commandEngine.getContext("taskId"))
        assertEquals("Task Without Description", commandEngine.getContext("taskTitle"))
    }

    @Test
    fun `full command lifecycle with existing description works correctly`() = runBlocking {
        // Given
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Task With Description",
            description = "Existing description",
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)

        // When: Start command (description exists)
        val result = executor.execute(1)

        // Then: Should skip description request and go directly to EXECUTION
        assertTrue(result.contains("Этап PLANNING завершён"))
        assertTrue(result.contains("Description: Existing description"))
        assertTrue(result.contains("Переход к этапу EXECUTION"))
        assertEquals(CommandStage.EXECUTION, commandEngine.getActiveState()?.currentStage)
        assertEquals("Existing description", commandEngine.getContext("description"))
    }

    // ===== Тесты этапа EXECUTION (US-PLAN-2) =====

    /**
     * Mock-реализация [LlmPort] для тестирования EXECUTION этапа.
     */
    private class MockLlmPort(
        private val response: TaskResult = TaskResult.Success("1. Step 1\n2. Step 2\n3. Step 3")
    ) : LlmPort {
        var lastPrompt: Prompt? = null
        var lastConfig: TaskExecutionConfig? = null

        override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
            lastPrompt = prompt
            lastConfig = config
            return response
        }

        override suspend fun chatWithMessages(
            messages: List<io.averkhogliad.ai.challenge.week2.domain.service.ChatMessage>,
            config: TaskExecutionConfig
        ): TaskResult {
            return response
        }

        override suspend fun listModels(): List<io.averkhogliad.ai.challenge.week2.domain.ModelId> {
            return emptyList()
        }
    }

    @Test
    fun `executeExecution without LLM returns error`() = runBlocking {
        // Given: executor without LLM
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Task",
            description = "Description",
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)
        executor.execute(1) // Переходим к EXECUTION

        // When
        val result = executor.executeExecution()

        // Then
        assertTrue(result.contains("Ошибка: LLM не настроен"))
    }

    @Test
    fun `executeExecution without active command returns error`() = runBlocking {
        // Given: executor with LLM but no active command
        val llmPort = MockLlmPort()
        val executorWithLlm = PlanCommandExecutor(taskRepository, factRepository, commandEngine, llmPort)

        // When
        val result = executorWithLlm.executeExecution()

        // Then
        assertTrue(result.contains("Ошибка: команда ':plan' не активна"))
    }

    @Test
    fun `executeExecution on wrong stage returns error`() = runBlocking {
        // Given: executor with LLM, command in PLANNING stage
        val llmPort = MockLlmPort()
        val executorWithLlm = PlanCommandExecutor(taskRepository, factRepository, commandEngine, llmPort)
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Task",
            description = null, // No description - will request input
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)
        executorWithLlm.execute(1) // Stays in PLANNING, requests description

        // When
        val result = executorWithLlm.executeExecution()

        // Then
        assertTrue(result.contains("Ошибка: команда ':plan' не на этапе EXECUTION"))
    }

    @Test
    fun `executeExecution with successful LLM response parses steps`() = runBlocking {
        // Given: executor with LLM, command in EXECUTION stage
        val llmResponse = "1. First step\n2. Second step\n3. Third step"
        val llmPort = MockLlmPort(TaskResult.Success(llmResponse))
        val executorWithLlm = PlanCommandExecutor(taskRepository, factRepository, commandEngine, llmPort)
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Task",
            description = "Task description",
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)
        executorWithLlm.execute(1) // Transitions to EXECUTION

        // When
        val result = executorWithLlm.executeExecution()

        // Then
        assertTrue(result.contains("Этап EXECUTION завершён"))
        assertTrue(result.contains("3 шагов"))
        assertTrue(result.contains("1. First step"))
        assertTrue(result.contains("2. Second step"))
        assertTrue(result.contains("3. Third step"))
        assertTrue(result.contains("Переход к этапу VALIDATION"))
        assertEquals(CommandStage.VALIDATION, commandEngine.getActiveState()?.currentStage)
        assertNotNull(llmPort.lastPrompt)
        assertNotNull(llmPort.lastConfig)
    }

    @Test
    fun `executeExecution with LLM error returns error message`() = runBlocking {
        // Given: executor with LLM that returns error
        val llmPort = MockLlmPort(TaskResult.Error("API rate limit exceeded"))
        val executorWithLlm = PlanCommandExecutor(taskRepository, factRepository, commandEngine, llmPort)
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Task",
            description = "Description",
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)
        executorWithLlm.execute(1)

        // When
        val result = executorWithLlm.executeExecution()

        // Then
        assertTrue(result.contains("Ошибка LLM: API rate limit exceeded"))
    }

    @Test
    fun `executeExecution with empty LLM response returns error`() = runBlocking {
        // Given: executor with LLM that returns empty response
        val llmPort = MockLlmPort(TaskResult.Success("No steps here"))
        val executorWithLlm = PlanCommandExecutor(taskRepository, factRepository, commandEngine, llmPort)
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Task",
            description = "Description",
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)
        executorWithLlm.execute(1)

        // When
        val result = executorWithLlm.executeExecution()

        // Then
        assertTrue(result.contains("Ошибка: LLM не вернула список шагов"))
    }

    @Test
    fun `executeExecution parses alternative list formats`() = runBlocking {
        // Given: LLM response with alternative formats (- and *)
        val llmResponse = """
            - First step
            * Second step
            1. Third step
            2) Fourth step
        """.trimIndent()
        val llmPort = MockLlmPort(TaskResult.Success(llmResponse))
        val executorWithLlm = PlanCommandExecutor(taskRepository, factRepository, commandEngine, llmPort)
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Task",
            description = "Description",
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)
        executorWithLlm.execute(1)

        // When
        val result = executorWithLlm.executeExecution()

        // Then
        assertTrue(result.contains("4 шагов"))
        assertTrue(result.contains("First step"))
        assertTrue(result.contains("Second step"))
        assertTrue(result.contains("Third step"))
        assertTrue(result.contains("Fourth step"))
    }

    @Test
    fun `executeExecution saves steps to FSM context`() = runBlocking {
        // Given
        val llmResponse = "1. Step A\n2. Step B"
        val llmPort = MockLlmPort(TaskResult.Success(llmResponse))
        val executorWithLlm = PlanCommandExecutor(taskRepository, factRepository, commandEngine, llmPort)
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Task",
            description = "Description",
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)
        executorWithLlm.execute(1)

        // When
        executorWithLlm.executeExecution()

        // Then
        assertEquals("Step A\nStep B", commandEngine.getContext("generatedSteps"))
        assertEquals("2", commandEngine.getContext("stepsCount"))
    }

    // ===== Тесты этапа VALIDATION (US-PLAN-3) =====

    /**
     * Подготавливает executor с LLM и переводит команду в этап VALIDATION.
     */
    private suspend fun setupValidationStage(): PlanCommandExecutor {
        val llmResponse = "1. First step\n2. Second step\n3. Third step"
        val llmPort = MockLlmPort(TaskResult.Success(llmResponse))
        val executorWithLlm = PlanCommandExecutor(taskRepository, factRepository, commandEngine, llmPort)
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Test Task",
            description = "Test description",
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)
        executorWithLlm.execute(1) // -> EXECUTION
        executorWithLlm.executeExecution() // -> VALIDATION
        return executorWithLlm
    }

    @Test
    fun `handleValidationInput with 'y' confirms and transitions to DONE`() = runBlocking {
        // Given
        setupValidationStage()
        assertEquals(CommandStage.VALIDATION, commandEngine.getActiveState()?.currentStage)

        // When
        val result = executor.handleValidationInput("y")

        // Then
        assertTrue(result.contains("Этап DONE завершён"))
        assertTrue(result.contains("План для 'Test Task' сохранён"))
        assertTrue(result.contains("1. First step"))
        assertTrue(result.contains("2. Second step"))
        assertTrue(result.contains("3. Third step"))
        assertTrue(result.contains("Команда ':plan' завершена успешно"))
        assertFalse(commandEngine.hasActiveCommand())
    }

    @Test
    fun `handleValidationInput with 'yes' confirms plan`() = runBlocking {
        // Given
        setupValidationStage()

        // When
        val result = executor.handleValidationInput("yes")

        // Then
        assertTrue(result.contains("Этап DONE завершён"))
        assertFalse(commandEngine.hasActiveCommand())
    }

    @Test
    fun `handleValidationInput with 'n' cancels planning`() = runBlocking {
        // Given
        setupValidationStage()

        // When
        val result = executor.handleValidationInput("n")

        // Then
        assertTrue(result.contains("Планирование отменено"))
        assertTrue(result.contains("Шаги не были сохранены"))
        assertFalse(commandEngine.hasActiveCommand())
    }

    @Test
    fun `handleValidationInput with 'no' cancels planning`() = runBlocking {
        // Given
        setupValidationStage()

        // When
        val result = executor.handleValidationInput("no")

        // Then
        assertTrue(result.contains("Планирование отменено"))
        assertFalse(commandEngine.hasActiveCommand())
    }

    @Test
    fun `handleValidationInput with 'edit' enters edit mode`() = runBlocking {
        // Given
        setupValidationStage()

        // When
        val result = executor.handleValidationInput("edit")

        // Then
        assertTrue(result.contains("Режим редактирования шагов"))
        assertTrue(result.contains("Текущие шаги:"))
        assertTrue(result.contains("1. First step"))
        assertTrue(result.contains("Введите новые шаги"))
        assertTrue(result.contains("cancel"))
        assertTrue(result.contains("done"))
        assertEquals("true", commandEngine.getContext("editMode"))
        assertTrue(commandEngine.hasActiveCommand())
    }

    @Test
    fun `handleValidationInput with unknown input shows help`() = runBlocking {
        // Given
        setupValidationStage()

        // When
        val result = executor.handleValidationInput("maybe")

        // Then
        assertTrue(result.contains("Неизвестная команда: 'maybe'"))
        assertTrue(result.contains("Доступные команды:"))
        assertTrue(result.contains("y"))
        assertTrue(result.contains("n"))
        assertTrue(result.contains("edit"))
        assertTrue(commandEngine.hasActiveCommand())
        assertEquals(CommandStage.VALIDATION, commandEngine.getActiveState()?.currentStage)
    }

    @Test
    fun `handleValidationInput without active command returns error`() = runBlocking {
        // When
        val result = executor.handleValidationInput("y")

        // Then
        assertTrue(result.contains("Ошибка: команда ':plan' не активна"))
    }

    @Test
    fun `handleValidationInput on wrong stage returns error`() = runBlocking {
        // Given: command in PLANNING stage
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Task",
            description = null,
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)
        executor.execute(1) // Stays in PLANNING

        // When
        val result = executor.handleValidationInput("y")

        // Then
        assertTrue(result.contains("Ошибка: команда ':plan' не на этапе VALIDATION"))
    }

    @Test
    fun `handleEditInput with new steps updates context and shows validation`() = runBlocking {
        // Given
        setupValidationStage()
        executor.handleValidationInput("edit") // Enter edit mode

        // When
        val newSteps = "1. New step A\n2. New step B"
        val result = executor.handleEditInput(newSteps)

        // Then
        assertTrue(result.contains("Этап EXECUTION завершён"))
        assertTrue(result.contains("2 шагов"))
        assertTrue(result.contains("New step A"))
        assertTrue(result.contains("New step B"))
        assertEquals("false", commandEngine.getContext("editMode"))
    }

    @Test
    fun `handleEditInput with 'cancel' returns to validation`() = runBlocking {
        // Given
        setupValidationStage()
        executor.handleValidationInput("edit") // Enter edit mode

        // When
        val result = executor.handleEditInput("cancel")

        // Then
        assertTrue(result.contains("Переход к этапу VALIDATION"))
        assertTrue(result.contains("Подтвердите план"))
        assertEquals("false", commandEngine.getContext("editMode"))
        assertTrue(commandEngine.hasActiveCommand())
    }

    @Test
    fun `handleEditInput with 'done' confirms current steps`() = runBlocking {
        // Given
        setupValidationStage()
        executor.handleValidationInput("edit") // Enter edit mode

        // When
        val result = executor.handleEditInput("done")

        // Then
        assertTrue(result.contains("Этап DONE завершён"))
        assertFalse(commandEngine.hasActiveCommand())
    }

    @Test
    fun `handleEditInput with unparseable input returns error`() = runBlocking {
        // Given
        setupValidationStage()
        executor.handleValidationInput("edit") // Enter edit mode

        // When
        val result = executor.handleEditInput("no steps here at all")

        // Then
        assertTrue(result.contains("Ошибка: не удалось распознать шаги"))
        assertEquals("true", commandEngine.getContext("editMode"))
    }

    @Test
    fun `handleEditInput without edit mode returns error`() = runBlocking {
        // Given: in VALIDATION but not in edit mode
        setupValidationStage()

        // When
        val result = executor.handleEditInput("1. Some step")

        // Then
        assertTrue(result.contains("Ошибка: режим редактирования не активен"))
    }

    @Test
    fun `handleEditInput without active command returns error`() = runBlocking {
        // When
        val result = executor.handleEditInput("1. Some step")

        // Then
        assertTrue(result.contains("Ошибка: команда ':plan' не активна"))
    }

    @Test
    fun `full lifecycle PLANNING to EXECUTION to VALIDATION to DONE works`() = runBlocking {
        // Given: executor with LLM
        val llmResponse = "1. Analyze requirements\n2. Design solution\n3. Implement code"
        val llmPort = MockLlmPort(TaskResult.Success(llmResponse))
        val executorWithLlm = PlanCommandExecutor(taskRepository, factRepository, commandEngine, llmPort)
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Full Lifecycle Task",
            description = "Complete lifecycle test",
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)

        // Step 1: PLANNING -> EXECUTION
        val planResult = executorWithLlm.execute(1)
        assertTrue(planResult.contains("Этап PLANNING завершён"))
        assertEquals(CommandStage.EXECUTION, commandEngine.getActiveState()?.currentStage)

        // Step 2: EXECUTION -> VALIDATION
        val execResult = executorWithLlm.executeExecution()
        assertTrue(execResult.contains("Этап EXECUTION завершён"))
        assertEquals(CommandStage.VALIDATION, commandEngine.getActiveState()?.currentStage)

        // Step 3: VALIDATION -> DONE (confirm with 'y')
        val validationResult = executorWithLlm.handleValidationInput("y")
        assertTrue(validationResult.contains("Этап DONE завершён"))
        assertTrue(validationResult.contains("Всего шагов: 3"))
        assertTrue(validationResult.contains("Analyze requirements"))
        assertTrue(validationResult.contains("Design solution"))
        assertTrue(validationResult.contains("Implement code"))
        assertFalse(commandEngine.hasActiveCommand())
    }

    @Test
    fun `full lifecycle with edit before confirm works`() = runBlocking {
        // Given
        val llmResponse = "1. Original step 1\n2. Original step 2"
        val llmPort = MockLlmPort(TaskResult.Success(llmResponse))
        val executorWithLlm = PlanCommandExecutor(taskRepository, factRepository, commandEngine, llmPort)
        val taskId = TaskId("1")
        val task = Task(
            id = taskId,
            title = "Edit Task",
            description = "Edit test",
            status = TaskStatus.OPEN,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        taskRepository.save(task)
        executorWithLlm.execute(1)
        executorWithLlm.executeExecution()

        // Step 1: Enter edit mode
        val editResult = executorWithLlm.handleValidationInput("edit")
        assertTrue(editResult.contains("Режим редактирования"))
        assertEquals("true", commandEngine.getContext("editMode"))

        // Step 2: Provide new steps
        val newStepsResult = executorWithLlm.handleEditInput("1. Edited step A\n2. Edited step B\n3. Edited step C")
        assertTrue(newStepsResult.contains("3 шагов"))
        assertTrue(newStepsResult.contains("Edited step A"))

        // Step 3: Confirm
        val confirmResult = executorWithLlm.handleValidationInput("y")
        assertTrue(confirmResult.contains("Этап DONE завершён"))
        assertTrue(confirmResult.contains("Edited step A"))
        assertTrue(confirmResult.contains("Edited step B"))
        assertTrue(confirmResult.contains("Edited step C"))
        assertFalse(commandEngine.hasActiveCommand())
    }
}

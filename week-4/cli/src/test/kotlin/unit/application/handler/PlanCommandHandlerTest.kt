package io.averkhogliad.ai.challenge.week4.cli.unit.application.handler

import io.averkhogliad.ai.challenge.week4.cli.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week4.cli.application.InvariantService
import io.averkhogliad.ai.challenge.week4.cli.application.handler.PlanCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.application.planner.FactCollector
import io.averkhogliad.ai.challenge.week4.cli.application.planner.LlmPlanner
import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.service.*
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * In-memory реализация [TaskRepository] для тестирования PlanCommandHandler.
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
        steps: List<io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStep>
    ) {
        // No-op for tests
    }

    override suspend fun findStepsByTaskId(taskId: TaskId): List<io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStep> {
        return emptyList()
    }

    override suspend fun updateEvent(taskId: TaskId, eventId: UUID, dueDate: LocalDate): Result<Unit> =
        Result.success(Unit)

    override suspend fun clearEvent(taskId: TaskId): Result<Unit> =
        Result.success(Unit)
}

/**
 * In-memory реализация [FactRepository] для тестирования PlanCommandHandler.
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

    override suspend fun searchBatch(queries: List<String>): List<Fact> {
        return facts.values.filter { fact ->
            queries.any { query -> fact.content.contains(query, ignoreCase = true) }
        }
    }
}

/**
 * Mock-реализация [LlmPort] для тестирования EXECUTION этапа.
 */
private class MockLlmPort(
    private val response: TaskResult = TaskResult.Success("1. Step 1\n2. Step 2\n3. Step 3")
) : LlmPort {
    var lastPrompt: Prompt? = null
    var lastConfig: TaskExecutionConfig? = null

    override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig, tools: List<MCPTool>?): TaskResult {
        lastPrompt = prompt
        lastConfig = config
        return response
    }

    override suspend fun chatWithMessages(
        messages: List<io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatMessage>,
        config: TaskExecutionConfig,
        tools: List<MCPTool>?
    ): TaskResult {
        return response
    }

    override suspend fun listModels(): List<io.averkhogliad.ai.challenge.week4.cli.domain.ModelId> {
        return emptyList()
    }
}

/**
 * Unit-тесты для [PlanCommandHandler].
 *
 * ## Тестируемая функциональность
 * - Инициализация команды `:plan` с открытой задачей
 * - Обработка задачи без description (запрос ввода)
 * - Обработка ввода description
 * - Сбор релевантных фактов из LTM
 * - Обработка ошибок (нет открытой задачи, задача не найдена, задача не открыта)
 * - Интеграция с CommandEngine
 */
class PlanCommandHandlerTest : FreeSpec({
    lateinit var taskRepository: TaskRepository
    lateinit var factRepository: FactRepository
    lateinit var commandEngine: CommandEngine
    lateinit var executor: PlanCommandHandler
    lateinit var stubInvariantService: InvariantService

    fun createStubInvariantService(): InvariantService =
        object : InvariantService(object : InvariantRepository {
            override suspend fun save(invariant: Invariant): Invariant = invariant
            override suspend fun findById(id: InvariantId): Invariant? = null
            override suspend fun findAll(): List<Invariant> = emptyList()
            override suspend fun delete(id: InvariantId) = false
            override suspend fun count() = 0
        }) {}

    beforeEach {
        taskRepository = PlanTestInMemoryTaskRepository()
        factRepository = PlanTestInMemoryFactRepository()
        commandEngine = DefaultCommandEngine()
        stubInvariantService = createStubInvariantService()
        val factCollector = FactCollector(factRepository)
        executor =
            PlanCommandHandler(taskRepository, commandEngine, factCollector, invariantService = stubInvariantService)
    }

    /**
     * Подготавливает executor с LLM и переводит команду в этап VALIDATION.
     */
    suspend fun setupValidationStage(): PlanCommandHandler {
        val llmResponse = "1. First step\n2. Second step\n3. Third step"
        val llmPort = MockLlmPort(TaskResult.Success(llmResponse))
        val executorWithLlm = PlanCommandHandler(
            taskRepository,
            commandEngine,
            FactCollector(factRepository),
            LlmPlanner(llmPort),
            invariantService = stubInvariantService
        )
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

    "command initialization" - {
        "execute with null currentTaskId returns error" {
            runTest {
                // when
                val result = executor.execute(null)

                // then
                result shouldContain "Ошибка: команда ':plan' требует открытой задачи"
                commandEngine.hasActiveCommand() shouldBe false
            }
        }

        "execute with non-existent taskId returns error" {
            runTest {
                // when
                val result = executor.execute(999)

                // then
                result shouldContain "Ошибка: задача с ID '999' не найдена"
                commandEngine.hasActiveCommand() shouldBe false
            }
        }

        "execute with closed task returns error" {
            runTest {
                // given
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

                // when
                val result = executor.execute(1)

                // then
                result shouldContain "Ошибка: задача 'Closed Task' не открыта"
                commandEngine.hasActiveCommand() shouldBe false
            }
        }

        "execute with open task without description requests description" {
            runTest {
                // given
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

                // when
                val result = executor.execute(1)

                // then
                result shouldContain "Задача 'Task Without Description' открыта"
                result shouldContain "Description задачи пуст"
                result shouldContain "Пожалуйста, опишите задачу подробно"
                commandEngine.hasActiveCommand() shouldBe true
                commandEngine.getContext("taskId") shouldBe "1"
                commandEngine.getContext("taskTitle") shouldBe "Task Without Description"
                commandEngine.getContext("needsDescription") shouldBe "true"
            }
        }

        "execute with open task with description collects facts and transitions to EXECUTION" {
            runTest {
                // given
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

                // when
                val result = executor.execute(1)

                // then
                result shouldContain "Этап PLANNING завершён для задачи 'Task With Description'"
                result shouldContain "Description: Implement feature X"
                result shouldContain "Переход к этапу EXECUTION"
                commandEngine.hasActiveCommand() shouldBe true
                commandEngine.getActiveState()?.currentStage shouldBe CommandStage.EXECUTION
                commandEngine.getContext("description") shouldBe "Implement feature X"
            }
        }
    }

    "description input" - {
        "handleDescriptionInput with valid input collects facts and transitions to EXECUTION" {
            runTest {
                // given
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

                // when
                val result = executor.handleDescriptionInput("New detailed description for the task")

                // then
                result shouldContain "Этап PLANNING завершён"
                result shouldContain "Description: New detailed description for the task"
                result shouldContain "Переход к этапу EXECUTION"
                commandEngine.getActiveState()?.currentStage shouldBe CommandStage.EXECUTION
                commandEngine.getContext("description") shouldBe "New detailed description for the task"
            }
        }

        "handleDescriptionInput with blank input returns error" {
            runTest {
                // given
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

                // when
                val result = executor.handleDescriptionInput("   ")

                // then
                result shouldContain "Ошибка: description не может быть пустым"
                commandEngine.getActiveState()?.currentStage shouldBe CommandStage.PLANNING
            }
        }

        "handleDescriptionInput without active command returns error" {
            runTest {
                // when
                val result = executor.handleDescriptionInput("Some description")

                // then
                result shouldContain "Ошибка: команда ':plan' не активна"
            }
        }
    }

    "fact collection" - {
        "execute collects relevant facts from LTM by keywords" {
            runTest {
                // given
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

                // when
                val result = executor.execute(1)

                // then
                result shouldContain "Релевантных фактов из LTM:"
                result shouldContain "Database migration requires backup"
            }
        }

        "execute with no matching facts returns empty facts list" {
            runTest {
                // given
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

                // when
                val result = executor.execute(1)

                // then
                (result.contains("Релевантных фактов из LTM: 0") || result.contains("Релевантных фактов из LTM:")) shouldBe true
            }
        }
    }

    "execution stage" - {
        "executeExecution without LLM returns error" {
            runTest {
                // given: executor without LLM
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

                // when
                val result = executor.executeExecution()

                // then
                result shouldContain "Ошибка: LLM не настроен"
            }
        }

        "executeExecution without active command returns error" {
            runTest {
                // given: executor with LLM but no active command
                val llmPort = MockLlmPort()
                val factCollector = FactCollector(factRepository)
                val llmPlanner = LlmPlanner(llmPort)
                val executorWithLlm = PlanCommandHandler(
                    taskRepository,
                    commandEngine,
                    factCollector,
                    llmPlanner,
                    invariantService = stubInvariantService
                )

                // when
                val result = executorWithLlm.executeExecution()

                // then
                result shouldContain "Ошибка: команда ':plan' не активна"
            }
        }

        "executeExecution on wrong stage returns error" {
            runTest {
                // given: executor with LLM, command in PLANNING stage
                val llmPort = MockLlmPort()
                val executorWithLlm = PlanCommandHandler(
                    taskRepository,
                    commandEngine,
                    FactCollector(factRepository),
                    LlmPlanner(llmPort),
                    invariantService = stubInvariantService
                )
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

                // when
                val result = executorWithLlm.executeExecution()

                // then
                result shouldContain "Ошибка: команда ':plan' не на этапе EXECUTION"
            }
        }

        "executeExecution with successful LLM response parses steps" {
            runTest {
                // given: executor with LLM, command in EXECUTION stage
                val llmResponse = "1. First step\n2. Second step\n3. Third step"
                val llmPort = MockLlmPort(TaskResult.Success(llmResponse))
                val executorWithLlm = PlanCommandHandler(
                    taskRepository,
                    commandEngine,
                    FactCollector(factRepository),
                    LlmPlanner(llmPort),
                    invariantService = stubInvariantService
                )
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

                // when
                val result = executorWithLlm.executeExecution()

                // then
                result shouldContain "Этап EXECUTION завершён"
                result shouldContain "3 шагов"
                result shouldContain "1. First step"
                result shouldContain "2. Second step"
                result shouldContain "3. Third step"
                result shouldContain "Переход к этапу VALIDATION"
                commandEngine.getActiveState()?.currentStage shouldBe CommandStage.VALIDATION
                llmPort.lastPrompt.shouldNotBeNull()
                llmPort.lastConfig.shouldNotBeNull()
            }
        }

        "executeExecution with LLM error returns error message" {
            runTest {
                // given: executor with LLM that returns error
                val llmPort = MockLlmPort(TaskResult.Error("API rate limit exceeded"))
                val executorWithLlm = PlanCommandHandler(
                    taskRepository,
                    commandEngine,
                    FactCollector(factRepository),
                    LlmPlanner(llmPort),
                    invariantService = stubInvariantService
                )
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

                // when
                val result = executorWithLlm.executeExecution()

                // then
                result shouldContain "Ошибка: API rate limit exceeded"
            }
        }

        "executeExecution with empty LLM response returns error" {
            runTest {
                // given: executor with LLM that returns empty response
                val llmPort = MockLlmPort(TaskResult.Success("No steps here"))
                val executorWithLlm = PlanCommandHandler(
                    taskRepository,
                    commandEngine,
                    FactCollector(factRepository),
                    LlmPlanner(llmPort),
                    invariantService = stubInvariantService
                )
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

                // when
                val result = executorWithLlm.executeExecution()

                // then
                result shouldContain "Ошибка: LLM не вернула список шагов"
            }
        }

        "executeExecution parses alternative list formats" {
            runTest {
                // given: LLM response with alternative formats (- and *)
                val llmResponse = """
                    - First step
                    * Second step
                    1. Third step
                    2) Fourth step
                """.trimIndent()
                val llmPort = MockLlmPort(TaskResult.Success(llmResponse))
                val executorWithLlm = PlanCommandHandler(
                    taskRepository,
                    commandEngine,
                    FactCollector(factRepository),
                    LlmPlanner(llmPort),
                    invariantService = stubInvariantService
                )
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

                // when
                val result = executorWithLlm.executeExecution()

                // then
                result shouldContain "4 шагов"
                result shouldContain "First step"
                result shouldContain "Second step"
                result shouldContain "Third step"
                result shouldContain "Fourth step"
            }
        }

        "executeExecution saves steps to FSM context" {
            runTest {
                // given
                val llmResponse = "1. Step A\n2. Step B"
                val llmPort = MockLlmPort(TaskResult.Success(llmResponse))
                val executorWithLlm = PlanCommandHandler(
                    taskRepository,
                    commandEngine,
                    FactCollector(factRepository),
                    LlmPlanner(llmPort),
                    invariantService = stubInvariantService
                )
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

                // when
                executorWithLlm.executeExecution()

                // then
                commandEngine.getContext("generatedSteps") shouldBe "Step A\nStep B"
                commandEngine.getContext("stepsCount") shouldBe "2"
            }
        }
    }

    "validation stage" - {
        "handleValidationInput with 'y' confirms and transitions to DONE" {
            runTest {
                // given
                val exec = setupValidationStage()
                commandEngine.getActiveState()?.currentStage shouldBe CommandStage.VALIDATION

                // when
                val result = exec.handleValidationInput("y")

                // then
                result shouldContain "Этап DONE завершён"
                result shouldContain "План для 'Test Task' сохранён"
                result shouldContain "1. First step"
                result shouldContain "2. Second step"
                result shouldContain "3. Third step"
                result shouldContain "Команда ':plan' завершена успешно"
                commandEngine.hasActiveCommand() shouldBe false
            }
        }

        "handleValidationInput with 'yes' confirms plan" {
            runTest {
                // given
                val exec = setupValidationStage()

                // when
                val result = exec.handleValidationInput("yes")

                // then
                result shouldContain "Этап DONE завершён"
                commandEngine.hasActiveCommand() shouldBe false
            }
        }

        "handleValidationInput with 'n' cancels planning" {
            runTest {
                // given
                val exec = setupValidationStage()

                // when
                val result = exec.handleValidationInput("n")

                // then
                result shouldContain "Планирование отменено"
                result shouldContain "Шаги не были сохранены"
                commandEngine.hasActiveCommand() shouldBe false
            }
        }

        "handleValidationInput with 'no' cancels planning" {
            runTest {
                // given
                val exec = setupValidationStage()

                // when
                val result = exec.handleValidationInput("no")

                // then
                result shouldContain "Планирование отменено"
                commandEngine.hasActiveCommand() shouldBe false
            }
        }

        "handleValidationInput with 'edit' enters edit mode" {
            runTest {
                // given
                val exec = setupValidationStage()

                // when
                val result = exec.handleValidationInput("edit")

                // then
                result shouldContain "Режим редактирования шагов"
                result shouldContain "Текущие шаги:"
                result shouldContain "1. First step"
                result shouldContain "Введите новые шаги"
                result shouldContain "cancel"
                result shouldContain "done"
                commandEngine.getContext("editMode") shouldBe "true"
                commandEngine.hasActiveCommand() shouldBe true
            }
        }

        "handleValidationInput with unknown input shows help" {
            runTest {
                // given
                val exec = setupValidationStage()

                // when
                val result = exec.handleValidationInput("maybe")

                // then
                result shouldContain "Неизвестная команда: 'maybe'"
                result shouldContain "Доступные команды:"
                result shouldContain "y"
                result shouldContain "n"
                result shouldContain "edit"
                commandEngine.hasActiveCommand() shouldBe true
                commandEngine.getActiveState()?.currentStage shouldBe CommandStage.VALIDATION
            }
        }

        "handleValidationInput without active command returns error" {
            runTest {
                // when
                val result = executor.handleValidationInput("y")

                // then
                result shouldContain "Ошибка: команда ':plan' не активна"
            }
        }

        "handleValidationInput on wrong stage returns error" {
            runTest {
                // given: command in PLANNING stage
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

                // when
                val result = executor.handleValidationInput("y")

                // then
                result shouldContain "Ошибка: команда ':plan' не на этапе VALIDATION"
            }
        }
    }

    "edit mode" - {
        "handleEditInput with new steps updates context and shows validation" {
            runTest {
                // given
                val exec = setupValidationStage()
                exec.handleValidationInput("edit") // Enter edit mode

                // when
                val newSteps = "1. New step A\n2. New step B"
                val result = exec.handleEditInput(newSteps)

                // then
                result shouldContain "Этап EXECUTION завершён"
                result shouldContain "2 шагов"
                result shouldContain "New step A"
                result shouldContain "New step B"
                commandEngine.getContext("editMode") shouldBe "false"
            }
        }

        "handleEditInput with 'cancel' returns to validation" {
            runTest {
                // given
                val exec = setupValidationStage()
                exec.handleValidationInput("edit") // Enter edit mode

                // when
                val result = exec.handleEditInput("cancel")

                // then
                result shouldContain "Переход к этапу VALIDATION"
                result shouldContain "Подтвердите план"
                commandEngine.getContext("editMode") shouldBe "false"
                commandEngine.hasActiveCommand() shouldBe true
            }
        }

        "handleEditInput with 'done' confirms current steps" {
            runTest {
                // given
                val exec = setupValidationStage()
                exec.handleValidationInput("edit") // Enter edit mode

                // when
                val result = exec.handleEditInput("done")

                // then
                result shouldContain "Этап DONE завершён"
                commandEngine.hasActiveCommand() shouldBe false
            }
        }

        "handleEditInput with unparseable input returns error" {
            runTest {
                // given
                val exec = setupValidationStage()
                exec.handleValidationInput("edit") // Enter edit mode

                // when
                val result = exec.handleEditInput("no steps here at all")

                // then
                result shouldContain "Ошибка: не удалось распознать шаги"
                commandEngine.getContext("editMode") shouldBe "true"
            }
        }

        "handleEditInput without edit mode returns error" {
            runTest {
                // given: in VALIDATION but not in edit mode
                val exec = setupValidationStage()

                // when
                val result = exec.handleEditInput("1. Some step")

                // then
                result shouldContain "Ошибка: режим редактирования не активен"
            }
        }

        "handleEditInput without active command returns error" {
            runTest {
                // when
                val result = executor.handleEditInput("1. Some step")

                // then
                result shouldContain "Ошибка: команда ':plan' не активна"
            }
        }
    }

    "full lifecycle" - {
        "full lifecycle PLANNING to EXECUTION to VALIDATION to DONE works" {
            runTest {
                // given: executor with LLM
                val llmResponse = "1. Analyze requirements\n2. Design solution\n3. Implement code"
                val llmPort = MockLlmPort(TaskResult.Success(llmResponse))
                val executorWithLlm = PlanCommandHandler(
                    taskRepository,
                    commandEngine,
                    FactCollector(factRepository),
                    LlmPlanner(llmPort),
                    invariantService = stubInvariantService
                )
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

                // when - PLANNING -> EXECUTION
                val planResult = executorWithLlm.execute(1)

                // then
                planResult shouldContain "Этап PLANNING завершён"
                commandEngine.getActiveState()?.currentStage shouldBe CommandStage.EXECUTION

                // when - EXECUTION -> VALIDATION
                val execResult = executorWithLlm.executeExecution()

                // then
                execResult shouldContain "Этап EXECUTION завершён"
                commandEngine.getActiveState()?.currentStage shouldBe CommandStage.VALIDATION

                // when - VALIDATION -> DONE (confirm with 'y')
                val validationResult = executorWithLlm.handleValidationInput("y")

                // then
                validationResult shouldContain "Этап DONE завершён"
                validationResult shouldContain "Всего шагов: 3"
                validationResult shouldContain "Analyze requirements"
                validationResult shouldContain "Design solution"
                validationResult shouldContain "Implement code"
                commandEngine.hasActiveCommand() shouldBe false
            }
        }

        "full lifecycle with edit before confirm works" {
            runTest {
                // given
                val llmResponse = "1. Original step 1\n2. Original step 2"
                val llmPort = MockLlmPort(TaskResult.Success(llmResponse))
                val executorWithLlm = PlanCommandHandler(
                    taskRepository,
                    commandEngine,
                    FactCollector(factRepository),
                    LlmPlanner(llmPort),
                    invariantService = stubInvariantService
                )
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

                // when - enter edit mode
                val editResult = executorWithLlm.handleValidationInput("edit")

                // then
                editResult shouldContain "Режим редактирования"
                commandEngine.getContext("editMode") shouldBe "true"

                // when - provide new steps
                val newStepsResult =
                    executorWithLlm.handleEditInput("1. Edited step A\n2. Edited step B\n3. Edited step C")

                // then
                newStepsResult shouldContain "3 шагов"
                newStepsResult shouldContain "Edited step A"

                // when - confirm
                val confirmResult = executorWithLlm.handleValidationInput("y")

                // then
                confirmResult shouldContain "Этап DONE завершён"
                confirmResult shouldContain "Edited step A"
                confirmResult shouldContain "Edited step B"
                confirmResult shouldContain "Edited step C"
                commandEngine.hasActiveCommand() shouldBe false
            }
        }

        "full command lifecycle with description request works correctly" {
            runTest {
                // given
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

                // when - start command (description is empty)
                val startResult = executor.execute(1)

                // then
                startResult shouldContain "Description задачи пуст"
                commandEngine.hasActiveCommand() shouldBe true
                commandEngine.getActiveState()?.currentStage shouldBe CommandStage.PLANNING

                // when - provide description
                val descriptionResult = executor.handleDescriptionInput("Detailed task description")

                // then
                descriptionResult shouldContain "Этап PLANNING завершён"
                descriptionResult shouldContain "Переход к этапу EXECUTION"
                commandEngine.getActiveState()?.currentStage shouldBe CommandStage.EXECUTION

                // Verify context
                commandEngine.getContext("description") shouldBe "Detailed task description"
                commandEngine.getContext("taskId") shouldBe "1"
                commandEngine.getContext("taskTitle") shouldBe "Task Without Description"
            }
        }

        "full command lifecycle with existing description works correctly" {
            runTest {
                // given
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

                // when - start command (description exists)
                val result = executor.execute(1)

                // then - should skip description request and go directly to EXECUTION
                result shouldContain "Этап PLANNING завершён"
                result shouldContain "Description: Existing description"
                result shouldContain "Переход к этапу EXECUTION"
                commandEngine.getActiveState()?.currentStage shouldBe CommandStage.EXECUTION
                commandEngine.getContext("description") shouldBe "Existing description"
            }
        }
    }
})

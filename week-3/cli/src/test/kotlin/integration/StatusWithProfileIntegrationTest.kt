package io.averkhogliad.ai.challenge.week3.cli.integration

import io.averkhogliad.ai.challenge.week3.cli.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week3.cli.application.InvariantService
import io.averkhogliad.ai.challenge.week3.cli.application.ProfileService
import io.averkhogliad.ai.challenge.week3.cli.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week3.cli.application.handler.DebugCommandHandler
import io.averkhogliad.ai.challenge.week3.cli.cli.*
import io.averkhogliad.ai.challenge.week3.cli.cli.handlers.*
import io.averkhogliad.ai.challenge.week3.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
import io.averkhogliad.ai.challenge.week3.cli.domain.service.*
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.InMemoryProfileRepository
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Интеграционные тесты для US-PM-12: отображение профиля в команде :status.
 *
 * Проверяет:
 * - [CliApplication] с [InMemoryProfileRepository] рендерит информацию о профиле
 * - Если активный профиль есть — его название отображается
 * - Если профиль не задан — отображается "Профиль не задан"
 */
@DisplayName("StatusWithProfileIntegrationTest")
class StatusWithProfileIntegrationTest {

    /**
     * Mock-рендерер, собирающий сообщения для проверки в тестах.
     */
    private class TestCliRenderer : CliRenderer {
        val renderedMessages = mutableListOf<String>()

        override fun renderWelcome() {}
        override fun renderMenu(executors: List<TaskExecutor>) {}
        override fun renderTaskHeader(metadata: io.averkhogliad.ai.challenge.week3.cli.domain.TaskMetadata) {}
        override fun renderResult(result: io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult) {}
        override fun renderError(message: String) {
            renderedMessages.add("error:$message")
        }

        override fun renderPrompt(state: CliState) {}
        override fun renderHelp(state: CliState) {}
        override fun renderParameters(state: CliState) {}
        override fun renderGoodbye() {}
        override fun renderRequestInfo(prompt: String, config: TaskExecutionConfig) {}

        override fun renderLoadingStart(message: String) {}
        override fun renderLoadingStop() {}
        override fun renderSuccess(message: String) {}
        override fun renderInfo(message: String) {
            renderedMessages.add("info:$message")
        }

        override fun renderTaskList(tasks: List<Task>) {}
        override fun renderTaskDetail(task: Task) {}
        override fun renderTaskCreated(taskId: TaskId) {}
        override fun renderTaskUpdated(taskId: TaskId) {}
        override fun renderTaskDeleted(taskId: TaskId) {}
        override fun renderTaskClosed(taskId: TaskId) {}
        override fun renderTaskCancelled(taskId: TaskId) {}
        override fun renderMemoryStatus(status: MemoryStatus) {
            renderedMessages.add("memoryStatus")
        }

        override fun renderMemoryCleared() {}
        override fun renderStepCreated(step: TaskStep) {}
        override fun renderStepList(steps: List<TaskStep>) {}
        override fun renderStepCompleted(step: TaskStep) {}
        override fun renderStepError(message: String) {}
        override fun renderFactSaved(fact: Fact) {}
        override fun renderFactList(facts: List<Fact>) {}
        override fun renderFactForgotten(factId: String) {}
        override fun renderFactNotFound(factId: String) {}
        override fun renderFactSearchResults(
            facts: List<Fact>,
            query: String
        ) {
        }

        override fun renderFactSearchEmpty(query: String) {}
        override fun renderProfileList(profiles: List<Profile>) {}
        override fun renderProfileDetail(profile: Profile) {}
        override fun renderProfileDeleted(name: String) {}
        override fun renderProfileUpdated(name: String) {}
        override fun renderProfileError(message: String) {}
        override fun renderMultilineInputPrompt() {}
        override fun renderProfileNotFoundById(id: String) {}
        override fun renderProfileNotFoundByName(name: String) {}
        override fun renderProfileAlreadyExists(name: String) {}
        override fun renderMissingProfileId() {}
        override fun renderMissingProfileName() {}
        override fun renderEmptyProfileContent() {}
        override fun renderProfileDescriptionPrompt() {}
        override fun renderProfileInstructionsPrompt() {}
        override fun renderCannotDeleteActiveProfile() {}
        override fun renderStatusProfile(profileName: String?) {
            renderedMessages.add("statusProfile:${profileName ?: "null"}")
        }

        override fun renderProfileContentTooLong(length: Int) {}

        override fun renderFsmState(state: CommandState) {}

        override fun renderStatusDebug(enabled: Boolean) {
            renderedMessages.add("statusDebug:$enabled")
        }

        override fun renderStatusActiveCommand(commandName: String?) {}
        override fun renderFsmStateInfo(state: CommandState) {}
        override fun renderNoActiveCommand() {}
        override fun renderAbortConfirmation() {}
        override fun renderAbortSuccess() {}
        override fun renderAbortCancelled() {}
        override fun renderInvariantList(invariants: List<Invariant>) {}
        override fun renderInvariantAdded(invariant: Invariant) {}
        override fun renderInvariantRemoved(id: Int) {}
        override fun renderInvariantNotFound(id: Int) {}
        override fun renderInvariantEmptyRule() {}
        override fun renderInvariantRemoveConfirmation(id: Int) {}
        override fun renderStatusInvariants(count: Int) {}

        override fun waitForEnter() {}

        override fun renderStatusFsm(
            stage: CommandStage?,
            availableTransitions: List<Transition>
        ) {
        }

        override fun renderStateMap(stateMap: StateMap) {}

        override fun renderGotoSuccess(
            from: CommandStage,
            to: CommandStage
        ) {
        }

        override fun renderGotoError(reason: String) {}

        override fun renderGotoNoActiveCommand() {}

        override fun renderGotoInvalidState(stateName: String) {}

        override fun renderAvailableTransitions(
            transitions: List<Transition>
        ) {
        }

        override fun renderTelemetry(result: io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult) {
            renderedMessages.add("telemetry:${result::class.simpleName}")
        }
    }

    private val stubTaskRepository = object : TaskRepository {
        override suspend fun save(task: Task) {}
        override suspend fun findById(id: TaskId): Task? = null
        override suspend fun findAll(): List<Task> = emptyList()
        override suspend fun delete(id: TaskId) {}
        override suspend fun exists(id: TaskId): Boolean = false
        override suspend fun saveSteps(
            taskId: TaskId,
            steps: List<TaskStep>
        ) {
        }

        override suspend fun findStepsByTaskId(taskId: TaskId): List<TaskStep> =
            emptyList()
    }

    private val stubTodoTaskService =
        io.averkhogliad.ai.challenge.week3.cli.application.service.TodoTaskService(stubTaskRepository)

    private val stubDialogSessionRepository =
        object : DialogSessionRepository {
            override fun findById(id: SessionId): DialogSession? =
                null

            override fun save(session: DialogSession): DialogSession =
                session

            override fun findByTaskId(taskId: TaskId): DialogSession? =
                null

            override fun findActiveSession(): DialogSession? = null
            override fun delete(id: SessionId) {}
        }

    private val stubMemoryService =
        MemoryService(stubDialogSessionRepository)

    private val stubTaskStepRepository = object : TaskStepRepository {
        override fun save(step: TaskStep): TaskStep =
            step

        override fun findById(stepId: TaskStepId): TaskStep? =
            null

        override fun findByTaskId(taskId: TaskId): List<TaskStep> =
            emptyList()

        override fun delete(stepId: TaskStepId): Boolean = true
        override fun deleteByTaskId(taskId: TaskId): Int = 0
        override fun countByTaskId(taskId: TaskId): Int = 0
    }

    private val stubFactRepository = object : FactRepository {
        override suspend fun save(fact: Fact): Fact = fact
        override suspend fun findById(id: FactId): Fact? = null
        override suspend fun findAll(): List<Fact> = emptyList()
        override suspend fun search(query: String): List<Fact> = emptyList()
        override suspend fun searchBatch(queries: List<String>): List<Fact> = emptyList()
        override suspend fun delete(id: FactId): Boolean = true
        override suspend fun count(): Int = 0
    }

    private val stubInvariantRepository =
        object : InvariantRepository {
            override suspend fun save(invariant: Invariant): Invariant =
                invariant

            override suspend fun findById(id: InvariantId): Invariant? =
                null

            override suspend fun findAll(): List<Invariant> =
                emptyList()

            override suspend fun delete(id: InvariantId): Boolean = true
            override suspend fun count(): Int = 0
            override fun close() {}
        }

    private val stubInvariantService = InvariantService(stubInvariantRepository)

    private fun createStubDialogService(profileRepository: ProfileRepository) =
        io.averkhogliad.ai.challenge.week3.cli.application.DialogService(
            llmPort = null,
            memoryService = stubMemoryService,
            promptBuilder = PromptBuilder(),
            profileRepository = profileRepository,
            invariantService = stubInvariantService
        )

    private val stubCommandEngine = DefaultCommandEngine()

    private val stubDebugCommandHandler = DebugCommandHandler(DebugMode())

    private fun createStubPlanCommandHandler() =
        io.averkhogliad.ai.challenge.week3.cli.application.handler.PlanCommandHandler(
            taskRepository = stubTaskRepository,
            commandEngine = stubCommandEngine,
            factCollector = io.averkhogliad.ai.challenge.week3.cli.application.planner.FactCollector(stubFactRepository),
            invariantService = stubInvariantService
        )

    private fun createApp(
        renderer: CliRenderer,
        profileRepository: ProfileRepository,
    ): CliApplication {
        val input = object : CliInput {
            override fun readLine(): String? = null
            override fun readMultiline(): String = ""
        }
        val commandHandler = CommandHandler(emptyMap())
        val handlers = CliCommandHandlers(
            command = commandHandler,
            debug = stubDebugCommandHandler,
            todoTask = TodoTaskCommandHandler(stubTodoTaskService, stubMemoryService, renderer, input::readMultiline),

            taskStep = TaskStepCommandHandler(
                io.averkhogliad.ai.challenge.week3.cli.application.service.TaskStepService(
                    taskStepRepository = stubTaskStepRepository,
                    memoryService = stubMemoryService
                ),
                renderer
            ),
            memory = MemoryCommandHandler(
                memoryService = stubMemoryService,
                profileRepository = profileRepository,
                debugCommandHandler = stubDebugCommandHandler,
                commandEngine = stubCommandEngine,
                invariantService = stubInvariantService,
                renderer = renderer
            ),
            ltm = LtmCommandHandler(
                io.averkhogliad.ai.challenge.week3.cli.application.service.LtmService(stubFactRepository),
                renderer
            ),
            fsm = FsmCommandHandler(stubCommandEngine, renderer, input::readLine),

            invariant = InvariantCommandHandler(stubInvariantService, renderer, input::readLine),
            profile = ProfileCommandHandler(
                ProfileService(profileRepository),
                renderer,
                input::readLine,
                input::readMultiline
            ),
            mcp = mockk<MCPCommandHandler>(relaxed = true),
        )
        val dialogService = createStubDialogService(profileRepository)
        val planCommandHandler = createStubPlanCommandHandler()
        val userInputFlowHandler = UserInputFlowHandler(
            renderer = renderer,
            dialogService = dialogService,
            planCommandHandler = planCommandHandler,
            commandEngine = stubCommandEngine,
            commandHandler = commandHandler
        )
        val planFlowHandler = PlanFlowHandler(
            renderer = renderer,
            dialogService = dialogService,
            planCommandHandler = planCommandHandler
        )
        val dispatcher = CliCommandDispatcher(
            renderer = renderer,
            handlers = handlers,
            userInputFlowHandler = userInputFlowHandler,
            planFlowHandler = planFlowHandler
        )
        return CliApplication(
            renderer = renderer,
            input = input,
            dispatcher = dispatcher,
            commandHandler = commandHandler,
            applicationResources = { }
        )


    }

    @Test

    @DisplayName("should show active profile name in status")
    fun `status with active profile`() = runBlocking {
        val profileRepository = InMemoryProfileRepository()
        val renderer = TestCliRenderer()

        // Создаём приложение с ProfileRepository
        val app = createApp(renderer, profileRepository)

        assertNotNull(app)

        // Создаём профиль через ProfileService и явно активируем
        val profileService = ProfileService(profileRepository)
        val profile = profileService.handleCreateProfile("Test Profile", "Test content", "")
        profileService.handleActivateProfile(profile.id)

        // Проверяем, что активный профиль найден
        val active = profileRepository.findActive()
        assertNotNull(active)
        assertTrue(active.isActive)
        assertEquals("Test Profile", active.name)

        val statusHandler = MemoryCommandHandler(
            memoryService = stubMemoryService,
            profileRepository = profileRepository,
            debugCommandHandler = stubDebugCommandHandler,
            commandEngine = stubCommandEngine,
            invariantService = stubInvariantService,
            renderer = renderer
        )

        statusHandler.handleShowStatus(CliState(taskListMode = true))

        val statusMsg = renderer.renderedMessages.find { it.startsWith("statusProfile:") }

        assertNotNull(statusMsg)
        assertEquals("statusProfile:Test Profile", statusMsg)
    }

    @Test
    @DisplayName("should show no profile when none is active")
    fun `status without active profile`() = runBlocking {
        val profileRepository = InMemoryProfileRepository()
        val renderer = TestCliRenderer()

        val app = createApp(renderer, profileRepository)

        assertNotNull(app)

        // Нет активного профиля
        val active = profileRepository.findActive()
        assertNull(active)

        val statusHandler = MemoryCommandHandler(
            memoryService = stubMemoryService,
            profileRepository = profileRepository,
            debugCommandHandler = stubDebugCommandHandler,
            commandEngine = stubCommandEngine,
            invariantService = stubInvariantService,
            renderer = renderer
        )

        statusHandler.handleShowStatus(CliState(taskListMode = true))

        val statusMsg = renderer.renderedMessages.find { it.startsWith("statusProfile:") }

        assertNotNull(statusMsg)
        assertEquals("statusProfile:null", statusMsg)
    }
}

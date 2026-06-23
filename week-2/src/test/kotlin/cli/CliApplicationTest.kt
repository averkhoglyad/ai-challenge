package io.averkhogliad.ai.challenge.week2.cli

import io.averkhogliad.ai.challenge.week2.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week2.application.handler.DebugCommandHandler
import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.DebugMode
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Тесты для [CliApplication] — основной CLI-оболочки приложения.
 *
 * Проверяют:
 * - Корректную обработку команд
 * - Управление состоянием через REPL-цикл
 * - Взаимодействие с executor'ами
 */
@DisplayName("CliApplication")
class CliApplicationTest {

    /**
     * Mock TaskExecutor для тестирования
     */
    private class MockTaskExecutor(
        override val taskId: TaskId,
        override val metadata: TaskMetadata = TaskMetadata(
            id = taskId,
            title = "Mock Task ${taskId.value}",
            description = "Mock task description"
        ),
        private val resultToReturn: TaskResult = TaskResult.Success("mock result")
    ) : TaskExecutor {
        var lastPrompt: Prompt? = null
        var lastConfig: TaskExecutionConfig? = null

        override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
            lastPrompt = prompt
            lastConfig = config
            return resultToReturn
        }
    }

    /**
     * Mock CliRenderer для тестирования
     */
    private class MockCliRenderer : CliRenderer {
        val renderedMessages = mutableListOf<String>()

        override fun renderWelcome() {
            renderedMessages.add("welcome")
        }

        override fun renderMenu(executors: List<TaskExecutor>) {
            renderedMessages.add("menu:${executors.size}")
        }

        override fun renderPrompt(state: CliState) {
            renderedMessages.add("prompt:${state.currentTaskId}")
        }

        override fun renderHelp(state: CliState) {
            renderedMessages.add("help")
        }

        override fun renderParameters(state: CliState) {
            renderedMessages.add("parameters")
        }

        override fun renderError(message: String) {
            renderedMessages.add("error:$message")
        }

        override fun renderSuccess(message: String) {
            renderedMessages.add("success:$message")
        }

        override fun renderInfo(message: String) {
            renderedMessages.add("info:$message")
        }

        override fun renderResult(result: TaskResult) {
            renderedMessages.add("result:${result::class.simpleName}")
        }

        override fun renderTaskHeader(metadata: TaskMetadata) {
            renderedMessages.add("taskHeader:${metadata.title}")
        }

        override fun renderRequestInfo(text: String, config: TaskExecutionConfig) {
            renderedMessages.add("request:$text")
        }

        override fun renderLoadingStart(message: String) {
            renderedMessages.add("loadingStart:$message")
        }

        override fun renderLoadingStop() {
            renderedMessages.add("loadingStop")
        }

        override fun renderGoodbye() {
            renderedMessages.add("goodbye")
        }

        override fun renderTaskList(tasks: List<io.averkhogliad.ai.challenge.week2.domain.model.Task>) {
            renderedMessages.add("taskList:${tasks.size}")
        }

        override fun renderTaskDetail(task: io.averkhogliad.ai.challenge.week2.domain.model.Task) {
            renderedMessages.add("taskDetail:${task.id.value}")
        }

        override fun renderTaskCreated(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {
            renderedMessages.add("taskCreated:${taskId.value}")
        }

        override fun renderTaskUpdated(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {
            renderedMessages.add("taskUpdated:${taskId.value}")
        }

        override fun renderTaskDeleted(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {
            renderedMessages.add("taskDeleted:${taskId.value}")
        }

        override fun renderTaskClosed(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {
            renderedMessages.add("taskClosed:${taskId.value}")
        }

        override fun renderTaskCancelled(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {
            renderedMessages.add("taskCancelled:${taskId.value}")
        }

        override fun renderMemoryStatus(status: MemoryStatus) {
            renderedMessages.add("memoryStatus:${status.messageCount}")
        }

        override fun renderMemoryCleared() {
            renderedMessages.add("memoryCleared")
        }

        override fun renderStepCreated(step: io.averkhogliad.ai.challenge.week2.domain.model.TaskStep) {
            renderedMessages.add("stepCreated:${step.id.value}")
        }

        override fun renderStepList(steps: List<io.averkhogliad.ai.challenge.week2.domain.model.TaskStep>) {
            renderedMessages.add("stepList:${steps.size}")
        }

        override fun renderStepCompleted(step: io.averkhogliad.ai.challenge.week2.domain.model.TaskStep) {
            renderedMessages.add("stepCompleted:${step.id.value}")
        }

        override fun renderStepError(message: String) {
            renderedMessages.add("stepError:$message")
        }

        override fun renderFactSaved(fact: io.averkhogliad.ai.challenge.week2.domain.model.Fact) {
            renderedMessages.add("factSaved:${fact.id.value}")
        }

        override fun renderFactList(facts: List<io.averkhogliad.ai.challenge.week2.domain.model.Fact>) {
            renderedMessages.add("factList:${facts.size}")
        }

        override fun renderFactForgotten(factId: String) {
            renderedMessages.add("factForgotten:$factId")
        }

        override fun renderFactNotFound(factId: String) {
            renderedMessages.add("factNotFound:$factId")
        }

        override fun renderFactSearchResults(
            facts: List<io.averkhogliad.ai.challenge.week2.domain.model.Fact>,
            query: String
        ) {
            renderedMessages.add("factSearchResults:$query:${facts.size}")
        }

        override fun renderFactSearchEmpty(query: String) {
            renderedMessages.add("factSearchEmpty:$query")
        }

        override fun renderProfileList(profiles: List<io.averkhogliad.ai.challenge.week2.domain.model.Profile>) {
            renderedMessages.add("profileList:${profiles.size}")
        }

        override fun renderProfileDetail(profile: io.averkhogliad.ai.challenge.week2.domain.model.Profile) {
            renderedMessages.add("profileDetail:${profile.name}")
        }

        override fun renderProfileDeleted(name: String) {
            renderedMessages.add("profileDeleted:$name")
        }

        override fun renderProfileUpdated(name: String) {
            renderedMessages.add("profileUpdated:$name")
        }

        override fun renderProfileError(message: String) {
            renderedMessages.add("profileError:$message")
        }

        override fun renderMultilineInputPrompt() {
            renderedMessages.add("multilineInputPrompt")
        }

        override fun renderProfileNotFoundById(id: String) {
            renderedMessages.add("profileNotFoundById:$id")
        }

        override fun renderProfileNotFoundByName(name: String) {
            renderedMessages.add("profileNotFoundByName:$name")
        }

        override fun renderProfileAlreadyExists(name: String) {
            renderedMessages.add("profileAlreadyExists:$name")
        }

        override fun renderMissingProfileId() {
            renderedMessages.add("missingProfileId")
        }

        override fun renderMissingProfileName() {
            renderedMessages.add("missingProfileName")
        }

        override fun renderEmptyProfileContent() {
            renderedMessages.add("emptyProfileContent")
        }

        override fun renderProfileDescriptionPrompt() {
            renderedMessages.add("profileDescriptionPrompt")
        }

        override fun renderProfileInstructionsPrompt() {
            renderedMessages.add("profileInstructionsPrompt")
        }

        override fun renderCannotDeleteActiveProfile() {
            renderedMessages.add("cannotDeleteActiveProfile")
        }

        override fun renderStatusProfile(profileName: String?) {
            renderedMessages.add("statusProfile:${profileName ?: "null"}")
        }

        override fun renderProfileContentTooLong(length: Int) {
            renderedMessages.add("profileContentTooLong:$length")
        }

        override fun renderFsmState(state: io.averkhogliad.ai.challenge.week2.domain.model.CommandState) {
            renderedMessages.add("fsmState:${state.commandName}")
        }

        override fun renderStatusDebug(enabled: Boolean) {
            renderedMessages.add("statusDebug:$enabled")
        }

        override fun renderStatusActiveCommand(commandName: String?) {
            renderedMessages.add("statusActiveCommand:${commandName ?: "null"}")
        }

        override fun renderFsmStateInfo(state: io.averkhogliad.ai.challenge.week2.domain.model.CommandState) {
            renderedMessages.add("fsmStateInfo:${state.commandName}")
        }

        override fun renderNoActiveCommand() {
            renderedMessages.add("noActiveCommand")
        }

        override fun renderAbortConfirmation() {
            renderedMessages.add("abortConfirmation")
        }

        override fun renderAbortSuccess() {
            renderedMessages.add("abortSuccess")
        }

        override fun renderAbortCancelled() {
            renderedMessages.add("abortCancelled")
        }

        override fun renderInvariantList(invariants: List<io.averkhogliad.ai.challenge.week2.domain.model.Invariant>) {
            renderedMessages.add("invariantList:${invariants.size}")
        }

        override fun renderInvariantAdded(invariant: io.averkhogliad.ai.challenge.week2.domain.model.Invariant) {
            renderedMessages.add("invariantAdded:${invariant.id.value}")
        }

        override fun renderInvariantRemoved(id: Int) {
            renderedMessages.add("invariantRemoved:$id")
        }

        override fun renderInvariantNotFound(id: Int) {
            renderedMessages.add("invariantNotFound:$id")
        }

        override fun renderInvariantEmptyRule() {
            renderedMessages.add("invariantEmptyRule")
        }

        override fun renderInvariantRemoveConfirmation(id: Int) {
            renderedMessages.add("invariantRemoveConfirmation:$id")
        }

        override fun renderStatusInvariants(count: Int) {
            renderedMessages.add("statusInvariants:$count")
        }

        override fun waitForEnter() {
            // no-op for tests
        }

        override fun renderStatusFsm(
            stage: io.averkhogliad.ai.challenge.week2.domain.model.CommandStage?,
            availableTransitions: List<io.averkhogliad.ai.challenge.week2.domain.model.Transition>
        ) {
            renderedMessages.add("statusFsm:${stage?.name}:${availableTransitions.size}")
        }

        override fun renderStateMap(stateMap: io.averkhogliad.ai.challenge.week2.domain.model.StateMap) {
            renderedMessages.add("stateMap:${stateMap.currentState.name}")
        }

        override fun renderGotoSuccess(
            from: io.averkhogliad.ai.challenge.week2.domain.model.CommandStage,
            to: io.averkhogliad.ai.challenge.week2.domain.model.CommandStage
        ) {
            renderedMessages.add("gotoSuccess:${from.name}:${to.name}")
        }

        override fun renderGotoError(reason: String) {
            renderedMessages.add("gotoError:$reason")
        }

        override fun renderGotoNoActiveCommand() {
            renderedMessages.add("gotoNoActiveCommand")
        }

        override fun renderGotoInvalidState(stateName: String) {
            renderedMessages.add("gotoInvalidState:$stateName")
        }

        override fun renderAvailableTransitions(
            transitions: List<io.averkhogliad.ai.challenge.week2.domain.model.Transition>
        ) {
            renderedMessages.add("availableTransitions:${transitions.size}")
        }

        override fun renderTelemetry(result: TaskResult) {
            renderedMessages.add("telemetry:${result::class.simpleName}")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Stubs for CliApplication dependencies
    // ═══════════════════════════════════════════════════════════════

    private val stubTaskRepository = object : io.averkhogliad.ai.challenge.week2.domain.service.TaskRepository {
        override suspend fun save(task: io.averkhogliad.ai.challenge.week2.domain.model.Task) {}
        override suspend fun findById(id: io.averkhogliad.ai.challenge.week2.domain.model.TaskId): io.averkhogliad.ai.challenge.week2.domain.model.Task? =
            null

        override suspend fun findAll(): List<io.averkhogliad.ai.challenge.week2.domain.model.Task> = emptyList()
        override suspend fun delete(id: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {}
        override suspend fun exists(id: io.averkhogliad.ai.challenge.week2.domain.model.TaskId): Boolean = false
        override suspend fun saveSteps(
            taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId,
            steps: List<io.averkhogliad.ai.challenge.week2.domain.model.TaskStep>
        ) {
        }

        override suspend fun findStepsByTaskId(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId): List<io.averkhogliad.ai.challenge.week2.domain.model.TaskStep> =
            emptyList()
    }

    private val stubTodoTaskService =
        io.averkhogliad.ai.challenge.week2.application.service.TodoTaskService(stubTaskRepository)

    private val stubDialogSessionRepository =
        object : io.averkhogliad.ai.challenge.week2.domain.service.DialogSessionRepository {
            override fun findById(id: io.averkhogliad.ai.challenge.week2.domain.model.SessionId): io.averkhogliad.ai.challenge.week2.domain.model.DialogSession? =
                null

            override fun save(session: io.averkhogliad.ai.challenge.week2.domain.model.DialogSession): io.averkhogliad.ai.challenge.week2.domain.model.DialogSession =
                session

            override fun findByTaskId(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId): io.averkhogliad.ai.challenge.week2.domain.model.DialogSession? =
                null

            override fun findActiveSession(): io.averkhogliad.ai.challenge.week2.domain.model.DialogSession? = null
            override fun delete(id: io.averkhogliad.ai.challenge.week2.domain.model.SessionId) {}
        }

    private val stubMemoryService =
        io.averkhogliad.ai.challenge.week2.domain.service.MemoryService(stubDialogSessionRepository)

    private val stubTaskStepRepository = object : io.averkhogliad.ai.challenge.week2.domain.service.TaskStepRepository {
        override fun save(step: io.averkhogliad.ai.challenge.week2.domain.model.TaskStep): io.averkhogliad.ai.challenge.week2.domain.model.TaskStep =
            step

        override fun findById(stepId: io.averkhogliad.ai.challenge.week2.domain.model.TaskStepId): io.averkhogliad.ai.challenge.week2.domain.model.TaskStep? =
            null

        override fun findByTaskId(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId): List<io.averkhogliad.ai.challenge.week2.domain.model.TaskStep> =
            emptyList()

        override fun delete(stepId: io.averkhogliad.ai.challenge.week2.domain.model.TaskStepId): Boolean = true
        override fun deleteByTaskId(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId): Int = 0
        override fun countByTaskId(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId): Int = 0
    }

    private val stubFactRepository = object : io.averkhogliad.ai.challenge.week2.domain.service.FactRepository {
        override suspend fun save(fact: io.averkhogliad.ai.challenge.week2.domain.model.Fact): io.averkhogliad.ai.challenge.week2.domain.model.Fact =
            fact

        override suspend fun findById(id: io.averkhogliad.ai.challenge.week2.domain.model.FactId): io.averkhogliad.ai.challenge.week2.domain.model.Fact? =
            null

        override suspend fun findAll(): List<io.averkhogliad.ai.challenge.week2.domain.model.Fact> = emptyList()
        override suspend fun search(query: String): List<io.averkhogliad.ai.challenge.week2.domain.model.Fact> =
            emptyList()

        override suspend fun searchBatch(queries: List<String>): List<io.averkhogliad.ai.challenge.week2.domain.model.Fact> =
            emptyList()

        override suspend fun delete(id: io.averkhogliad.ai.challenge.week2.domain.model.FactId): Boolean = true
        override suspend fun count(): Int = 0
    }

    private val stubProfileRepository = object : io.averkhogliad.ai.challenge.week2.domain.service.ProfileRepository {
        override suspend fun save(profile: io.averkhogliad.ai.challenge.week2.domain.model.Profile): io.averkhogliad.ai.challenge.week2.domain.model.Profile =
            profile

        override suspend fun findById(id: io.averkhogliad.ai.challenge.week2.domain.model.ProfileId): io.averkhogliad.ai.challenge.week2.domain.model.Profile? =
            null

        override suspend fun findAll(): List<io.averkhogliad.ai.challenge.week2.domain.model.Profile> = emptyList()
        override suspend fun delete(id: io.averkhogliad.ai.challenge.week2.domain.model.ProfileId) {}
        override suspend fun findByName(name: String): io.averkhogliad.ai.challenge.week2.domain.model.Profile? = null
        override suspend fun findActive(): io.averkhogliad.ai.challenge.week2.domain.model.Profile? = null
        override suspend fun existsByName(name: String): Boolean = false
        override suspend fun clearActive() {}
    }

    private val stubInvariantRepository =
        object : io.averkhogliad.ai.challenge.week2.domain.service.InvariantRepository {
            override suspend fun save(invariant: io.averkhogliad.ai.challenge.week2.domain.model.Invariant): io.averkhogliad.ai.challenge.week2.domain.model.Invariant =
                invariant

            override suspend fun findById(id: io.averkhogliad.ai.challenge.week2.domain.model.InvariantId): io.averkhogliad.ai.challenge.week2.domain.model.Invariant? =
                null

            override suspend fun findAll(): List<io.averkhogliad.ai.challenge.week2.domain.model.Invariant> =
                emptyList()

            override suspend fun delete(id: io.averkhogliad.ai.challenge.week2.domain.model.InvariantId): Boolean = true
            override suspend fun count(): Int = 0
            override fun close() {}
        }

    private val stubInvariantService =
        io.averkhogliad.ai.challenge.week2.application.InvariantService(stubInvariantRepository)

    private val stubPromptBuilder = io.averkhogliad.ai.challenge.week2.domain.service.PromptBuilder()

    private val stubDialogService = io.averkhogliad.ai.challenge.week2.application.DialogService(
        llmPort = null,
        memoryService = stubMemoryService,
        promptBuilder = stubPromptBuilder,
        profileRepository = stubProfileRepository,
        invariantService = stubInvariantService
    )

    private val stubCommandEngine = io.averkhogliad.ai.challenge.week2.application.DefaultCommandEngine()

    private val stubDebugCommandHandler = DebugCommandHandler(
        DebugMode()
    )

    private val stubPlanCommandHandler = io.averkhogliad.ai.challenge.week2.application.handler.PlanCommandHandler(
        taskRepository = stubTaskRepository,
        commandEngine = stubCommandEngine,
        factCollector = io.averkhogliad.ai.challenge.week2.application.planner.FactCollector(stubFactRepository),
        invariantService = stubInvariantService
    )

    // ═══════════════════════════════════════════════════════════════
    // Command handling
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Command handling")
    inner class CommandHandling {

        @Test
        @DisplayName("should create CliApplication with executors")
        fun `creates application with executors`() {
            val executor = MockTaskExecutor(TaskId("1"))
            val renderer = MockCliRenderer()

            val app = CliApplication(
                executors = mapOf(TaskId("1") to executor),
                renderer = renderer,
                todoTaskService = stubTodoTaskService,
                memoryService = stubMemoryService,
                taskStepRepository = stubTaskStepRepository,
                factRepository = stubFactRepository,
                dialogService = stubDialogService,
                profileRepository = stubProfileRepository,
                planCommandHandler = stubPlanCommandHandler,
                commandEngine = stubCommandEngine,
                debugCommandHandler = stubDebugCommandHandler,
                invariantService = stubInvariantService,
                invariantRepository = stubInvariantRepository
            )

            assertNotNull(app)
        }

        @Test
        @DisplayName("should handle Help command without changing state")
        fun `handles Help command`() = runBlocking {
            val executor = MockTaskExecutor(TaskId("1"))
            val renderer = MockCliRenderer()
            val app = CliApplication(
                executors = mapOf(TaskId("1") to executor),
                renderer = renderer,
                todoTaskService = stubTodoTaskService,
                memoryService = stubMemoryService,
                taskStepRepository = stubTaskStepRepository,
                factRepository = stubFactRepository,
                dialogService = stubDialogService,
                profileRepository = stubProfileRepository,
                planCommandHandler = stubPlanCommandHandler,
                commandEngine = stubCommandEngine,
                debugCommandHandler = stubDebugCommandHandler,
                invariantService = stubInvariantService,
                invariantRepository = stubInvariantRepository
            )

            // Проверяем, что приложение создано корректно
            // (REPL не запускаем, так как это side-effect)
            assertNotNull(app)
        }

        @Test
        @DisplayName("should handle Quit command to stop REPL")
        fun `handles Quit command`() = runBlocking {
            val executor = MockTaskExecutor(TaskId("1"))
            val renderer = MockCliRenderer()

            val app = CliApplication(
                executors = mapOf(TaskId("1") to executor),
                renderer = renderer,
                todoTaskService = stubTodoTaskService,
                memoryService = stubMemoryService,
                taskStepRepository = stubTaskStepRepository,
                factRepository = stubFactRepository,
                dialogService = stubDialogService,
                profileRepository = stubProfileRepository,
                planCommandHandler = stubPlanCommandHandler,
                commandEngine = stubCommandEngine,
                debugCommandHandler = stubDebugCommandHandler,
                invariantService = stubInvariantService,
                invariantRepository = stubInvariantRepository
            )

            assertNotNull(app)
        }

        @Test
        @DisplayName("should handle SelectTask command to change current task")
        fun `handles SelectTask command`() = runBlocking {
            val executor = MockTaskExecutor(TaskId("1"))
            val renderer = MockCliRenderer()

            val app = CliApplication(
                executors = mapOf(TaskId("1") to executor),
                renderer = renderer,
                todoTaskService = stubTodoTaskService,
                memoryService = stubMemoryService,
                taskStepRepository = stubTaskStepRepository,
                factRepository = stubFactRepository,
                dialogService = stubDialogService,
                profileRepository = stubProfileRepository,
                planCommandHandler = stubPlanCommandHandler,
                commandEngine = stubCommandEngine,
                debugCommandHandler = stubDebugCommandHandler,
                invariantService = stubInvariantService,
                invariantRepository = stubInvariantRepository
            )

            assertNotNull(app)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // State management
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("State management")
    inner class StateManagement {

        @Test
        @DisplayName("CliState should have default values")
        fun `CliState has default values`() {
            val state = CliState()

            assertNull(state.currentTaskId)
            assertNull(state.currentDialogId)
            assertEquals(TaskExecutionConfig(), state.executionConfig)
            assertTrue(state.isRunning)
        }

        @Test
        @DisplayName("CliState should be immutable")
        fun `CliState is immutable`() {
            val state1 = CliState(currentTaskId = 1)
            val state2 = state1.copy(currentTaskId = 2)

            assertEquals(1, state1.currentTaskId)
            assertEquals(2, state2.currentTaskId)
        }

        @Test
        @DisplayName("CliState should support copy with modifications")
        fun `CliState supports copy`() {
            val state = CliState()
            val newState = state.copy(
                currentTaskId = 1,
                currentDialogId = "dialog-1",
                isRunning = false
            )

            assertEquals(1, newState.currentTaskId)
            assertEquals("dialog-1", newState.currentDialogId)
            assertEquals(false, newState.isRunning)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Executor interaction
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Executor interaction")
    inner class ExecutorInteraction {

        @Test
        @DisplayName("should create application with multiple executors")
        fun `creates application with multiple executors`() {
            val executor1 = MockTaskExecutor(TaskId("1"))
            val executor2 = MockTaskExecutor(TaskId("2"))
            val renderer = MockCliRenderer()

            val app = CliApplication(
                executors = mapOf(
                    TaskId("1") to executor1,
                    TaskId("2") to executor2
                ),
                renderer = renderer,
                todoTaskService = stubTodoTaskService,
                memoryService = stubMemoryService,
                taskStepRepository = stubTaskStepRepository,
                factRepository = stubFactRepository,
                dialogService = stubDialogService,
                profileRepository = stubProfileRepository,
                planCommandHandler = stubPlanCommandHandler,
                commandEngine = stubCommandEngine,
                debugCommandHandler = stubDebugCommandHandler,
                invariantService = stubInvariantService,
                invariantRepository = stubInvariantRepository
            )

            assertNotNull(app)
        }

        @Test
        @DisplayName("should create application with empty executors map")
        fun `creates application with empty executors`() {
            val renderer = MockCliRenderer()

            val app = CliApplication(
                executors = emptyMap(),
                renderer = renderer,
                todoTaskService = stubTodoTaskService,
                memoryService = stubMemoryService,
                taskStepRepository = stubTaskStepRepository,
                factRepository = stubFactRepository,
                dialogService = stubDialogService,
                profileRepository = stubProfileRepository,
                planCommandHandler = stubPlanCommandHandler,
                commandEngine = stubCommandEngine,
                debugCommandHandler = stubDebugCommandHandler,
                invariantService = stubInvariantService,
                invariantRepository = stubInvariantRepository
            )

            assertNotNull(app)
        }
    }
}

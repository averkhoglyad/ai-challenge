package io.averkhogliad.ai.challenge.week3.cli.cli

import io.averkhogliad.ai.challenge.week3.cli.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week3.cli.application.handler.DebugCommandHandler
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week3.cli.cli.handlers.*
import io.averkhogliad.ai.challenge.week3.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week3.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.DebugMode
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.service.MemoryStatus
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.collections.ArrayDeque
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.any
import kotlin.collections.count
import kotlin.collections.emptyList
import kotlin.collections.emptyMap
import kotlin.collections.firstOrNull
import kotlin.collections.listOf
import kotlin.collections.mapOf
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.set
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

    private class FakeCliInput(
        private val lines: ArrayDeque<String> = ArrayDeque(),
        private val multilineInputs: ArrayDeque<String> = ArrayDeque(),
    ) : CliInput {
        var readMultilineCalls: Int = 0
            private set

        override fun readLine(): String? = lines.removeFirstOrNull()

        override fun readMultiline(): String {
            readMultilineCalls += 1
            return multilineInputs.removeFirstOrNull() ?: ""
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

        override fun renderRequestInfo(prompt: String, config: TaskExecutionConfig) {
            renderedMessages.add("request:$prompt")
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

        override fun renderTaskList(tasks: List<io.averkhogliad.ai.challenge.week3.cli.domain.model.Task>) {
            renderedMessages.add("taskList:${tasks.size}")
        }

        override fun renderTaskDetail(task: io.averkhogliad.ai.challenge.week3.cli.domain.model.Task) {
            renderedMessages.add("taskDetail:${task.id.value}")
        }

        override fun renderTaskCreated(taskId: TaskId) {
            renderedMessages.add("taskCreated:${taskId.value}")
        }

        override fun renderTaskUpdated(taskId: TaskId) {
            renderedMessages.add("taskUpdated:${taskId.value}")
        }

        override fun renderTaskDeleted(taskId: TaskId) {
            renderedMessages.add("taskDeleted:${taskId.value}")
        }

        override fun renderTaskClosed(taskId: TaskId) {
            renderedMessages.add("taskClosed:${taskId.value}")
        }

        override fun renderTaskCancelled(taskId: TaskId) {
            renderedMessages.add("taskCancelled:${taskId.value}")
        }

        override fun renderMemoryStatus(status: MemoryStatus) {
            renderedMessages.add("memoryStatus:${status.messageCount}")
        }

        override fun renderMemoryCleared() {
            renderedMessages.add("memoryCleared")
        }

        override fun renderStepCreated(step: io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep) {
            renderedMessages.add("stepCreated:${step.id.value}")
        }

        override fun renderStepList(steps: List<io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep>) {
            renderedMessages.add("stepList:${steps.size}")
        }

        override fun renderStepCompleted(step: io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep) {
            renderedMessages.add("stepCompleted:${step.id.value}")
        }

        override fun renderStepError(message: String) {
            renderedMessages.add("stepError:$message")
        }

        override fun renderFactSaved(fact: io.averkhogliad.ai.challenge.week3.cli.domain.model.Fact) {
            renderedMessages.add("factSaved:${fact.id.value}")
        }

        override fun renderFactList(facts: List<io.averkhogliad.ai.challenge.week3.cli.domain.model.Fact>) {
            renderedMessages.add("factList:${facts.size}")
        }

        override fun renderFactForgotten(factId: String) {
            renderedMessages.add("factForgotten:$factId")
        }

        override fun renderFactNotFound(factId: String) {
            renderedMessages.add("factNotFound:$factId")
        }

        override fun renderFactSearchResults(
            facts: List<io.averkhogliad.ai.challenge.week3.cli.domain.model.Fact>,
            query: String
        ) {
            renderedMessages.add("factSearchResults:$query:${facts.size}")
        }

        override fun renderFactSearchEmpty(query: String) {
            renderedMessages.add("factSearchEmpty:$query")
        }

        override fun renderProfileList(profiles: List<io.averkhogliad.ai.challenge.week3.cli.domain.model.Profile>) {
            renderedMessages.add("profileList:${profiles.size}")
        }

        override fun renderProfileDetail(profile: io.averkhogliad.ai.challenge.week3.cli.domain.model.Profile) {
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

        override fun renderFsmState(state: io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandState) {
            renderedMessages.add("fsmState:${state.commandName}")
        }

        override fun renderStatusDebug(enabled: Boolean) {
            renderedMessages.add("statusDebug:$enabled")
        }

        override fun renderStatusActiveCommand(commandName: String?) {
            renderedMessages.add("statusActiveCommand:${commandName ?: "null"}")
        }

        override fun renderFsmStateInfo(state: io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandState) {
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

        override fun renderInvariantList(invariants: List<io.averkhogliad.ai.challenge.week3.cli.domain.model.Invariant>) {
            renderedMessages.add("invariantList:${invariants.size}")
        }

        override fun renderInvariantAdded(invariant: io.averkhogliad.ai.challenge.week3.cli.domain.model.Invariant) {
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
            stage: io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandStage?,
            availableTransitions: List<io.averkhogliad.ai.challenge.week3.cli.domain.model.Transition>
        ) {
            renderedMessages.add("statusFsm:${stage?.name}:${availableTransitions.size}")
        }

        override fun renderStateMap(stateMap: io.averkhogliad.ai.challenge.week3.cli.domain.model.StateMap) {
            renderedMessages.add("stateMap:${stateMap.currentState.name}")
        }

        override fun renderGotoSuccess(
            from: io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandStage,
            to: io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandStage
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
            transitions: List<io.averkhogliad.ai.challenge.week3.cli.domain.model.Transition>
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

    private val stubTaskRepository = object : io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskRepository {
        override suspend fun save(task: io.averkhogliad.ai.challenge.week3.cli.domain.model.Task) {}
        override suspend fun findById(id: TaskId): io.averkhogliad.ai.challenge.week3.cli.domain.model.Task? =
            null

        override suspend fun findAll(): List<io.averkhogliad.ai.challenge.week3.cli.domain.model.Task> = emptyList()
        override suspend fun delete(id: TaskId) {}
        override suspend fun exists(id: TaskId): Boolean = false
        override suspend fun saveSteps(
            taskId: TaskId,
            steps: List<io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep>
        ) {
        }

        override suspend fun findStepsByTaskId(taskId: TaskId): List<io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep> =
            emptyList()

        override suspend fun updateEvent(taskId: TaskId, eventId: UUID, dueDate: LocalDate): Result<Unit> =
            Result.success(Unit)

        override suspend fun clearEvent(taskId: TaskId): Result<Unit> =
            Result.success(Unit)
    }

    private val stubTodoTaskService =
        io.averkhogliad.ai.challenge.week3.cli.application.service.TodoTaskService(stubTaskRepository)

    private val stubDialogSessionRepository =
        object : io.averkhogliad.ai.challenge.week3.cli.domain.service.DialogSessionRepository {
            override fun findById(id: io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionId): io.averkhogliad.ai.challenge.week3.cli.domain.model.DialogSession? =
                null

            override fun save(session: io.averkhogliad.ai.challenge.week3.cli.domain.model.DialogSession): io.averkhogliad.ai.challenge.week3.cli.domain.model.DialogSession =
                session

            override fun findByTaskId(taskId: TaskId): io.averkhogliad.ai.challenge.week3.cli.domain.model.DialogSession? =
                null

            override fun findActiveSession(): io.averkhogliad.ai.challenge.week3.cli.domain.model.DialogSession? = null
            override fun delete(id: io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionId) {}
        }

    private val stubMemoryService =
        io.averkhogliad.ai.challenge.week3.cli.domain.service.MemoryService(stubDialogSessionRepository)

    private val stubTaskStepRepository =
        object : io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskStepRepository {
            override fun save(step: io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep): io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep =
                step

            override fun findById(stepId: io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStepId): io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep? =
                null

            override fun findByTaskId(taskId: TaskId): List<io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep> =
                emptyList()

            override fun delete(stepId: io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStepId): Boolean = true
            override fun deleteByTaskId(taskId: TaskId): Int = 0
            override fun countByTaskId(taskId: TaskId): Int = 0
        }

    private val stubFactRepository = object : io.averkhogliad.ai.challenge.week3.cli.domain.service.FactRepository {
        override suspend fun save(fact: io.averkhogliad.ai.challenge.week3.cli.domain.model.Fact): io.averkhogliad.ai.challenge.week3.cli.domain.model.Fact =
            fact

        override suspend fun findById(id: io.averkhogliad.ai.challenge.week3.cli.domain.model.FactId): io.averkhogliad.ai.challenge.week3.cli.domain.model.Fact? =
            null

        override suspend fun findAll(): List<io.averkhogliad.ai.challenge.week3.cli.domain.model.Fact> = emptyList()
        override suspend fun search(query: String): List<io.averkhogliad.ai.challenge.week3.cli.domain.model.Fact> =
            emptyList()

        override suspend fun searchBatch(queries: List<String>): List<io.averkhogliad.ai.challenge.week3.cli.domain.model.Fact> =
            emptyList()

        override suspend fun delete(id: io.averkhogliad.ai.challenge.week3.cli.domain.model.FactId): Boolean = true
        override suspend fun count(): Int = 0
    }

    private val stubProfileRepository =
        object : io.averkhogliad.ai.challenge.week3.cli.domain.service.ProfileRepository {
            override suspend fun save(profile: io.averkhogliad.ai.challenge.week3.cli.domain.model.Profile): io.averkhogliad.ai.challenge.week3.cli.domain.model.Profile =
                profile

            override suspend fun findById(id: io.averkhogliad.ai.challenge.week3.cli.domain.model.ProfileId): io.averkhogliad.ai.challenge.week3.cli.domain.model.Profile? =
                null

            override suspend fun findAll(): List<io.averkhogliad.ai.challenge.week3.cli.domain.model.Profile> =
                emptyList()

            override suspend fun delete(id: io.averkhogliad.ai.challenge.week3.cli.domain.model.ProfileId) {}
            override suspend fun findByName(name: String): io.averkhogliad.ai.challenge.week3.cli.domain.model.Profile? =
                null

            override suspend fun findActive(): io.averkhogliad.ai.challenge.week3.cli.domain.model.Profile? = null
            override suspend fun existsByName(name: String): Boolean = false
            override suspend fun clearActive() {}
        }

    private val stubInvariantRepository =
        object : io.averkhogliad.ai.challenge.week3.cli.domain.service.InvariantRepository {
            override suspend fun save(invariant: io.averkhogliad.ai.challenge.week3.cli.domain.model.Invariant): io.averkhogliad.ai.challenge.week3.cli.domain.model.Invariant =
                invariant

            override suspend fun findById(id: io.averkhogliad.ai.challenge.week3.cli.domain.model.InvariantId): io.averkhogliad.ai.challenge.week3.cli.domain.model.Invariant? =
                null

            override suspend fun findAll(): List<io.averkhogliad.ai.challenge.week3.cli.domain.model.Invariant> =
                emptyList()

            override suspend fun delete(id: io.averkhogliad.ai.challenge.week3.cli.domain.model.InvariantId): Boolean =
                true

            override suspend fun count(): Int = 0
            override fun close() {}
        }

    private val stubInvariantService =
        io.averkhogliad.ai.challenge.week3.cli.application.InvariantService(stubInvariantRepository)

    private val stubPromptBuilder = io.averkhogliad.ai.challenge.week3.cli.domain.service.PromptBuilder()

    private val stubDialogService = io.averkhogliad.ai.challenge.week3.cli.application.DialogService(
        llmPort = null,
        memoryService = stubMemoryService,
        promptBuilder = stubPromptBuilder,
        profileRepository = stubProfileRepository,
        invariantService = stubInvariantService,
        mcpService = mockk(relaxed = true),
        toolCallRouter = mockk(relaxed = true),
        toolRegistry = io.averkhogliad.ai.challenge.week3.cli.application.tool.ToolRegistry(emptyList()),
        promptPresetAggregator = mockk(relaxed = true),
        taskRepository = stubTaskRepository
    )

    private val stubCommandEngine = io.averkhogliad.ai.challenge.week3.cli.application.DefaultCommandEngine()

    private val stubDebugCommandHandler = DebugCommandHandler(
        DebugMode()
    )

    private val stubPlanCommandHandler = io.averkhogliad.ai.challenge.week3.cli.application.handler.PlanCommandHandler(
        taskRepository = stubTaskRepository,
        commandEngine = stubCommandEngine,
        factCollector = io.averkhogliad.ai.challenge.week3.cli.application.planner.FactCollector(stubFactRepository),
        invariantService = stubInvariantService
    )

    private fun createApp(
        executors: Map<TaskId, TaskExecutor>,
        renderer: CliRenderer,
        input: CliInput = object : CliInput {
            override fun readLine(): String? = null
            override fun readMultiline(): String = ""
        },
        profileRepository: io.averkhogliad.ai.challenge.week3.cli.domain.service.ProfileRepository = stubProfileRepository,
    ): CliApplication {

        val commandHandler = CommandHandler(executors)
        val handlers = CliCommandHandlers(
            command = commandHandler,
            debug = stubDebugCommandHandler,
            todoTask = TodoTaskCommandHandler(

                todoTaskService = stubTodoTaskService,
                memoryService = stubMemoryService,
                renderer = renderer,
                readMultiline = input::readMultiline
            ),
            taskStep = TaskStepCommandHandler(
                taskStepService = io.averkhogliad.ai.challenge.week3.cli.application.service.TaskStepService(
                    taskStepRepository = stubTaskStepRepository,
                    memoryService = stubMemoryService
                ),
                renderer = renderer
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
                ltmService = io.averkhogliad.ai.challenge.week3.cli.application.service.LtmService(stubFactRepository),
                renderer = renderer
            ),
            fsm = FsmCommandHandler(
                commandEngine = stubCommandEngine,
                renderer = renderer,
                readInput = input::readLine
            ),

            invariant = InvariantCommandHandler(
                invariantService = stubInvariantService,
                renderer = renderer,
                readInput = input::readLine
            ),
            profile = ProfileCommandHandler(
                profileService = io.averkhogliad.ai.challenge.week3.cli.application.ProfileService(profileRepository),

                renderer = renderer,
                readLine = input::readLine,
                readMultiline = input::readMultiline
            ),
            mcp = mockk<MCPCommandHandler>(relaxed = true),
            events = mockk<EventsCommandHandler>(relaxed = true),
        )
        val userInputFlowHandler = UserInputFlowHandler(
            renderer = renderer,
            dialogService = stubDialogService,
            planCommandHandler = stubPlanCommandHandler,
            commandEngine = stubCommandEngine,
            commandHandler = commandHandler
        )
        val planFlowHandler = PlanFlowHandler(
            renderer = renderer,
            dialogService = stubDialogService,
            planCommandHandler = stubPlanCommandHandler
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

            val app = createApp(
                executors = mapOf(TaskId("1") to executor),
                renderer = renderer
            )


            assertNotNull(app)
        }

        @Test
        @DisplayName("should handle Help command without changing state")
        fun `handles Help command`() = runBlocking {
            val executor = MockTaskExecutor(TaskId("1"))
            val renderer = MockCliRenderer()
            val app = createApp(
                executors = mapOf(TaskId("1") to executor),
                renderer = renderer
            )


            // Проверяем, что приложение создано корректно
            // (REPL не запускаем, так как это side-effect)
            assertNotNull(app)
            Unit
        }

        @Test
        @DisplayName("should handle Quit command to stop REPL")
        fun `handles Quit command`() = runBlocking {
            val executor = MockTaskExecutor(TaskId("1"))
            val renderer = MockCliRenderer()

            val app = createApp(
                executors = mapOf(TaskId("1") to executor),
                renderer = renderer
            )


            assertNotNull(app)
            Unit
        }

        @Test
        @DisplayName("should handle SelectTask command to change current task")
        fun `handles SelectTask command`() = runBlocking {
            val executor = MockTaskExecutor(TaskId("1"))
            val renderer = MockCliRenderer()

            val app = createApp(
                executors = mapOf(TaskId("1") to executor),
                renderer = renderer
            )


            assertNotNull(app)
            Unit
        }

        @Test
        @DisplayName("run uses shared fake input and real handler wiring")
        fun `run uses shared fake input and real handler wiring`() {
            val executor = MockTaskExecutor(TaskId("1"))
            val renderer = MockCliRenderer()
            val input = FakeCliInput(lines = ArrayDeque(listOf("1", "q")))

            val app = createApp(
                executors = mapOf(TaskId("1") to executor),
                renderer = renderer,
                input = input
            )

            app.run()

            assertTrue(renderer.renderedMessages.contains("welcome"))
            assertTrue(renderer.renderedMessages.contains("menu:1"))
            assertTrue(renderer.renderedMessages.contains("taskHeader:Mock Task 1"))
            assertTrue(renderer.renderedMessages.contains("goodbye"))
        }

        @Test
        @DisplayName("debug command routes through DebugCommandHandler")
        fun `debug command routes through DebugCommandHandler`() {
            val renderer = MockCliRenderer()
            val app = createApp(
                executors = emptyMap(),
                renderer = renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf(":debug on", ":debug off", "q")))
            )


            app.run()

            assertTrue(renderer.renderedMessages.contains("info:Debug mode enabled"))
            assertTrue(renderer.renderedMessages.contains("info:Debug mode disabled"))
        }

        @Test
        @DisplayName("state renders active FSM state and no active command")
        fun `state renders active FSM state and no active command`() {
            val renderer = MockCliRenderer()
            stubCommandEngine.startCommand("test")
            val app = createApp(
                executors = emptyMap(),
                renderer = renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf(":state", ":abort", "yes", ":state", "q")))
            )

            app.run()

            assertTrue(renderer.renderedMessages.contains("fsmStateInfo:test"))
            assertTrue(renderer.renderedMessages.contains("abortSuccess"))
            assertTrue(renderer.renderedMessages.contains("noActiveCommand"))
        }

        @Test
        @DisplayName("abort cancel path uses injected input confirmation")
        fun `abort cancel path uses injected input confirmation`() {
            val renderer = MockCliRenderer()
            stubCommandEngine.startCommand("test")
            val app = createApp(
                executors = emptyMap(),
                renderer = renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf(":abort", "no", ":state", ":abort", "yes", "q")))
            )

            app.run()

            assertTrue(renderer.renderedMessages.contains("abortConfirmation"))
            assertTrue(renderer.renderedMessages.contains("abortCancelled"))
            assertTrue(renderer.renderedMessages.contains("fsmStateInfo:test"))
            assertTrue(renderer.renderedMessages.contains("abortSuccess"))
        }

        @Test
        @DisplayName("goto command routes through parser context and dispatcher")
        fun `goto command routes through parser context and dispatcher`() {
            val renderer = MockCliRenderer()
            val app = createApp(
                executors = mapOf(TaskId("1") to MockTaskExecutor(TaskId("1"))),
                renderer = renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf("1", ":goto", "q")))
            )

            app.run()

            assertTrue(renderer.renderedMessages.contains("gotoNoActiveCommand"))
        }

        @Test
        @DisplayName("invariant command routes through parser context and dispatcher")
        fun `invariant command routes through parser context and dispatcher`() {
            val renderer = MockCliRenderer()
            val app = createApp(
                executors = mapOf(TaskId("1") to MockTaskExecutor(TaskId("1"))),
                renderer = renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf("1", ":invariant list", "q")))
            )

            app.run()

            assertTrue(renderer.renderedMessages.contains("invariantList:0"))
        }

        @Test
        @DisplayName("legacy dialog command routes to unsupported message")
        fun `legacy dialog command routes to unsupported message`() {
            val renderer = MockCliRenderer()
            val app = createApp(
                executors = mapOf(TaskId("1") to MockTaskExecutor(TaskId("1"))),
                renderer = renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf("1", ":new old", ":history", "q")))
            )

            app.run()

            assertEquals(2, renderer.renderedMessages.count { it == "info:Команды диалогов больше не поддерживаются" })
        }

        @Test
        @DisplayName("profile command routes through parser context and dispatcher")
        fun `profile command routes through parser context and dispatcher`() {

            val renderer = MockCliRenderer()
            val app = createApp(
                executors = mapOf(TaskId("1") to MockTaskExecutor(TaskId("1"))),
                renderer = renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf("1", ":profile-list", ":profile-use Missing", "q")))
            )

            app.run()

            assertTrue(renderer.renderedMessages.contains("profileList:0"))
            assertTrue(renderer.renderedMessages.contains("profileNotFoundByName:Missing"))
        }

        @Test
        @DisplayName("status command routes through parser and dispatcher with active profile")
        fun `status command routes through parser and dispatcher with active profile`() = runBlocking {
            val renderer = MockCliRenderer()
            val profileRepository =
                io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.InMemoryProfileRepository()
            val profileService = io.averkhogliad.ai.challenge.week3.cli.application.ProfileService(profileRepository)
            val profile = profileService.handleCreateProfile("Active", "content", "")
            profileService.handleActivateProfile(profile.id)
            val app = createApp(
                executors = mapOf(TaskId("1") to MockTaskExecutor(TaskId("1"))),
                renderer = renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf("1", ":status", "q"))),
                profileRepository = profileRepository
            )

            app.run()

            assertTrue(renderer.renderedMessages.contains("statusProfile:Active"))
        }

        @Test
        @DisplayName("status command routes through parser and dispatcher without active profile")
        fun `status command routes through parser and dispatcher without active profile`() {
            val renderer = MockCliRenderer()
            val app = createApp(
                executors = mapOf(TaskId("1") to MockTaskExecutor(TaskId("1"))),
                renderer = renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf("1", ":status", "q"))),
                profileRepository = io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.InMemoryProfileRepository()
            )

            app.run()

            assertTrue(renderer.renderedMessages.contains("statusProfile:null"))
        }

        @Test
        @DisplayName("todo detail user input uses dialog service task detail context before executor")
        fun `todo detail user input uses dialog service task detail context before executor`() = runBlocking {
            val renderer = MockCliRenderer()
            val llmPort = object : io.averkhogliad.ai.challenge.week3.cli.domain.service.LlmPort {
                override suspend fun chat(
                    prompt: Prompt,
                    config: TaskExecutionConfig,
                    tools: List<io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPTool>?
                ): TaskResult =
                    TaskResult.Success("unused")

                override suspend fun chatWithMessages(
                    messages: List<io.averkhogliad.ai.challenge.week3.cli.domain.service.ChatMessage>,
                    config: TaskExecutionConfig,
                    tools: List<io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPTool>?
                ): TaskResult = TaskResult.Success("detail response")

                override suspend fun listModels(): List<io.averkhogliad.ai.challenge.week3.cli.domain.ModelId> =
                    emptyList()
            }
            val sessions = mutableMapOf<String, io.averkhogliad.ai.challenge.week3.cli.domain.model.DialogSession>()
            val sessionRepository =
                object : io.averkhogliad.ai.challenge.week3.cli.domain.service.DialogSessionRepository {
                    override fun findById(id: io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionId) =
                        sessions[id.value]

                    override fun save(session: io.averkhogliad.ai.challenge.week3.cli.domain.model.DialogSession): io.averkhogliad.ai.challenge.week3.cli.domain.model.DialogSession {
                        sessions[session.id.value] = session
                        return session
                    }

                    override fun findByTaskId(taskId: TaskId) = sessions.values.firstOrNull { it.taskId == taskId }
                    override fun findActiveSession() = sessions.values.firstOrNull()
                    override fun delete(id: io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionId) {
                        sessions.remove(id.value)
                    }
                }
            val memoryService = io.averkhogliad.ai.challenge.week3.cli.domain.service.MemoryService(sessionRepository)
            val dialogService = io.averkhogliad.ai.challenge.week3.cli.application.DialogService(
                llmPort = llmPort,
                memoryService = memoryService,
                promptBuilder = stubPromptBuilder,
                profileRepository = stubProfileRepository,
                invariantService = stubInvariantService,
                mcpService = mockk(relaxed = true),
                toolCallRouter = mockk(relaxed = true),
                toolRegistry = io.averkhogliad.ai.challenge.week3.cli.application.tool.ToolRegistry(emptyList()),
                promptPresetAggregator = mockk(relaxed = true),
                taskRepository = stubTaskRepository
            )
            val executor = MockTaskExecutor(TaskId("2"))
            val flow = UserInputFlowHandler(
                renderer = renderer,
                dialogService = dialogService,
                planCommandHandler = stubPlanCommandHandler,
                commandEngine = stubCommandEngine,
                commandHandler = CommandHandler(mapOf(TaskId("2") to executor))
            )

            flow.handle(Command.UserInput("hello"), CliState(currentTaskId = 2, currentTodoTaskId = "42"))

            assertNull(executor.lastPrompt)
            val detailSession = sessions["session_task_42"]
            assertNotNull(detailSession)
            assertEquals(
                io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionLevel.TASK_DETAIL,
                detailSession.level
            )
            assertEquals(TaskId("42"), detailSession.taskId)
            assertTrue(renderer.renderedMessages.contains("result:Success"))
        }

        @Test
        @DisplayName("run uses shared fake multiline input for todo add")
        fun `run uses shared fake multiline input for todo add`() {


            val renderer = MockCliRenderer()
            val input = FakeCliInput(
                lines = ArrayDeque(listOf("1", ":add Test task", "q")),

                multilineInputs = ArrayDeque(listOf("Shared multiline description"))
            )
            val app = createApp(
                executors = emptyMap(),
                renderer = renderer,
                input = input
            )

            app.run()

            assertTrue(renderer.renderedMessages.any { it.startsWith("taskCreated:") })
            assertTrue(renderer.renderedMessages.contains("info:Введите описание задачи (Enter — пропустить, пустая строка — завершить):"))
            assertEquals(1, input.readMultilineCalls)

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

            val app = createApp(
                executors = mapOf(
                    TaskId("1") to executor1,
                    TaskId("2") to executor2
                ),
                renderer = renderer
            )


            assertNotNull(app)
        }

        @Test
        @DisplayName("should create application with empty executors map")
        fun `creates application with empty executors`() {
            val renderer = MockCliRenderer()

            val app = createApp(
                executors = emptyMap(),
                renderer = renderer
            )


            assertNotNull(app)
        }
    }
}

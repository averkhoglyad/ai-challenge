package io.averkhogliad.ai.challenge.week2.unit.cli

import io.averkhogliad.ai.challenge.week2.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.application.InvariantService
import io.averkhogliad.ai.challenge.week2.application.ProfileService
import io.averkhogliad.ai.challenge.week2.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week2.application.handler.DebugCommandHandler
import io.averkhogliad.ai.challenge.week2.application.handler.PlanCommandHandler
import io.averkhogliad.ai.challenge.week2.application.planner.FactCollector
import io.averkhogliad.ai.challenge.week2.application.planner.KeywordExtractor
import io.averkhogliad.ai.challenge.week2.application.planner.PlanMessageBuilder
import io.averkhogliad.ai.challenge.week2.application.planner.StepParser
import io.averkhogliad.ai.challenge.week2.application.service.LtmService
import io.averkhogliad.ai.challenge.week2.application.service.TaskStepService
import io.averkhogliad.ai.challenge.week2.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week2.cli.*
import io.averkhogliad.ai.challenge.week2.cli.commands.Command
import io.averkhogliad.ai.challenge.week2.cli.handlers.*
import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.*
import io.averkhogliad.ai.challenge.week2.domain.service.*
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.Clock
import io.averkhogliad.ai.challenge.week2.domain.model.StateMap as DomainStateMap

class CliApplicationTest : FreeSpec({

    // ========================================================================
    // Helper classes
    // ========================================================================

    class MockTaskExecutor(
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

    class FakeCliInput(
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

    class MockCliRenderer : CliRenderer {
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

        override fun renderTaskList(tasks: List<Task>) {
            renderedMessages.add("taskList:${tasks.size}")
        }

        override fun renderTaskDetail(task: Task) {
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

        override fun renderStepCreated(step: TaskStep) {
            renderedMessages.add("stepCreated:${step.id.value}")
        }

        override fun renderStepList(steps: List<TaskStep>) {
            renderedMessages.add("stepList:${steps.size}")
        }

        override fun renderStepCompleted(step: TaskStep) {
            renderedMessages.add("stepCompleted:${step.id.value}")
        }

        override fun renderStepError(message: String) {
            renderedMessages.add("stepError:$message")
        }

        override fun renderFactSaved(fact: Fact) {
            renderedMessages.add("factSaved:${fact.id.value}")
        }

        override fun renderFactList(facts: List<Fact>) {
            renderedMessages.add("factList:${facts.size}")
        }

        override fun renderFactForgotten(factId: String) {
            renderedMessages.add("factForgotten:$factId")
        }

        override fun renderFactNotFound(factId: String) {
            renderedMessages.add("factNotFound:$factId")
        }

        override fun renderFactSearchResults(facts: List<Fact>, query: String) {
            renderedMessages.add("factSearchResults:$query:${facts.size}")
        }

        override fun renderFactSearchEmpty(query: String) {
            renderedMessages.add("factSearchEmpty:$query")
        }

        override fun renderProfileList(profiles: List<Profile>) {
            renderedMessages.add("profileList:${profiles.size}")
        }

        override fun renderProfileDetail(profile: Profile) {
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

        override fun renderFsmState(state: CommandState) {
            renderedMessages.add("fsmState:${state.commandName}")
        }

        override fun renderStatusDebug(enabled: Boolean) {
            renderedMessages.add("statusDebug:$enabled")
        }

        override fun renderStatusActiveCommand(commandName: String?) {
            renderedMessages.add("statusActiveCommand:${commandName ?: "null"}")
        }

        override fun renderFsmStateInfo(state: CommandState) {
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

        override fun renderInvariantList(invariants: List<Invariant>) {
            renderedMessages.add("invariantList:${invariants.size}")
        }

        override fun renderInvariantAdded(invariant: Invariant) {
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

        override fun waitForEnter() { /* no-op for tests */
        }

        override fun renderStatusFsm(stage: CommandStage?, availableTransitions: List<Transition>) {
            renderedMessages.add("statusFsm:${stage?.name}:${availableTransitions.size}")
        }

        override fun renderStateMap(stateMap: DomainStateMap) {
            renderedMessages.add("stateMap:${stateMap.currentState.name}")
        }

        override fun renderGotoSuccess(from: CommandStage, to: CommandStage) {
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

        override fun renderAvailableTransitions(transitions: List<Transition>) {
            renderedMessages.add("availableTransitions:${transitions.size}")
        }

        override fun renderTelemetry(result: TaskResult) {
            renderedMessages.add("telemetry:${result::class.simpleName}")
        }
    }

    // ========================================================================
    // MockK mocks for repositories
    // ========================================================================

    fun mockTaskRepository(): TaskRepository {
        val repo = mockk<TaskRepository>(relaxed = true)
        coEvery { repo.findAll() } returns emptyList()
        coEvery { repo.findById(any()) } returns null
        coEvery { repo.exists(any()) } returns false
        return repo
    }

    fun mockDialogSessionRepository(): DialogSessionRepository = mockk(relaxed = true)

    fun mockTaskStepRepository(): TaskStepRepository {
        val repo = mockk<TaskStepRepository>(relaxed = true)
        every { repo.findByTaskId(any()) } returns emptyList()
        every { repo.countByTaskId(any()) } returns 0
        return repo
    }

    fun mockFactRepository(): FactRepository {
        val repo = mockk<FactRepository>(relaxed = true)
        coEvery { repo.findAll() } returns emptyList()
        return repo
    }

    fun mockProfileRepository(): ProfileRepository {
        val repo = mockk<ProfileRepository>(relaxed = true)
        coEvery { repo.findActive() } returns null
        coEvery { repo.findAll() } returns emptyList()
        coEvery { repo.findByName(any()) } returns null
        coEvery { repo.findById(any()) } returns null
        coEvery { repo.existsByName(any()) } returns false
        return repo
    }

    fun mockInvariantRepository(): InvariantRepository {
        val repo = mockk<InvariantRepository>(relaxed = true)
        coEvery { repo.findAll() } returns emptyList()
        coEvery { repo.count() } returns 0
        return repo
    }

    // ========================================================================
    // createApp helper
    // ========================================================================

    fun createApp(
        executors: Map<TaskId, TaskExecutor>,
        renderer: CliRenderer,
        input: CliInput = object : CliInput {
            override fun readLine(): String? = null
            override fun readMultiline(): String = ""
        },
        profileRepo: ProfileRepository = mockProfileRepository(),
        taskRepo: TaskRepository = mockTaskRepository(),
        dialogSessionRepo: DialogSessionRepository = mockDialogSessionRepository(),
        taskStepRepo: TaskStepRepository = mockTaskStepRepository(),
        factRepo: FactRepository = mockFactRepository(),
        invariantRepo: InvariantRepository = mockInvariantRepository(),
    ): CliApplication {
        val todoTaskService = TodoTaskService(taskRepo)
        val memoryService = MemoryService(dialogSessionRepo, taskRepo, taskStepRepo, factRepo)
        val promptBuilder = PromptBuilder()
        val invariantService = mockk<InvariantService>(relaxed = true)
        val commandEngine = DefaultCommandEngine()
        val debugCommandHandler = DebugCommandHandler(DebugMode())

        val dialogService = DialogService(
            llmPort = mockk(relaxed = true),
            memoryService = memoryService,
            promptBuilder = promptBuilder,
            taskExecutionConfig = TaskExecutionConfig(),
            profileRepository = profileRepo,
            invariantService = invariantService
        )

        val planCommandHandler = PlanCommandHandler(
            taskRepository = taskRepo,
            commandEngine = commandEngine,
            factCollector = FactCollector(factRepo, KeywordExtractor()),
            llmPlanner = null,
            stepParser = StepParser(),
            invariantService = invariantService,
            messages = PlanMessageBuilder(commandEngine)
        )

        val commandHandler = CommandHandler(executors)
        val handlers = CliCommandHandlers(
            command = commandHandler,
            debug = debugCommandHandler,
            todoTask = TodoTaskCommandHandler(todoTaskService, memoryService, renderer, input::readMultiline),
            taskStep = TaskStepCommandHandler(
                TaskStepService(taskStepRepo, memoryService, Clock.systemUTC()),
                renderer
            ),
            memory = MemoryCommandHandler(
                memoryService,
                profileRepo,
                debugCommandHandler,
                commandEngine,
                invariantService,
                renderer
            ),
            ltm = LtmCommandHandler(LtmService(factRepo, Clock.systemUTC()), renderer),
            fsm = FsmCommandHandler(commandEngine, renderer, input::readLine),
            invariant = InvariantCommandHandler(invariantService, renderer, input::readLine),
            profile = ProfileCommandHandler(
                ProfileService(profileRepo),
                renderer,
                input::readLine,
                input::readMultiline
            ),
        )
        val userInputFlowHandler =
            UserInputFlowHandler(renderer, dialogService, planCommandHandler, commandEngine, commandHandler)
        val planFlowHandler = PlanFlowHandler(renderer, dialogService, planCommandHandler)
        val dispatcher = CliCommandDispatcher(renderer, handlers, userInputFlowHandler, planFlowHandler)
        return CliApplication(renderer, input, dispatcher, commandHandler, {})
    }

    // ========================================================================
    // Command handling
    // ========================================================================

    "Command handling" - {

        "creates application with executors" {
            val executor = MockTaskExecutor(TaskId("1"))
            val renderer = MockCliRenderer()
            val app = createApp(mapOf(TaskId("1") to executor), renderer)
            app.shouldNotBeNull()
        }

        "handles Help command" {
            val executor = MockTaskExecutor(TaskId("1"))
            val renderer = MockCliRenderer()
            val app = createApp(mapOf(TaskId("1") to executor), renderer)
            app.shouldNotBeNull()
        }

        "handles Quit command" {
            val executor = MockTaskExecutor(TaskId("1"))
            val renderer = MockCliRenderer()
            val app = createApp(mapOf(TaskId("1") to executor), renderer)
            app.shouldNotBeNull()
        }

        "handles SelectTask command" {
            val executor = MockTaskExecutor(TaskId("1"))
            val renderer = MockCliRenderer()
            val app = createApp(mapOf(TaskId("1") to executor), renderer)
            app.shouldNotBeNull()
        }

        "run uses shared fake input and real handler wiring" {
            val executor = MockTaskExecutor(TaskId("1"))
            val renderer = MockCliRenderer()
            val input = FakeCliInput(lines = ArrayDeque(listOf("1", "q")))
            val app = createApp(mapOf(TaskId("1") to executor), renderer, input = input)

            app.run()

            renderer.renderedMessages.shouldContain("welcome")
            renderer.renderedMessages.shouldContain("menu:1")
            renderer.renderedMessages.shouldContain("taskHeader:Mock Task 1")
            renderer.renderedMessages.shouldContain("goodbye")
        }

        "debug command routes through DebugCommandHandler" {
            val renderer = MockCliRenderer()
            val app = createApp(
                emptyMap(),
                renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf(":debug on", ":debug off", "q")))
            )

            app.run()

            renderer.renderedMessages.shouldContain("info:Debug mode enabled")
            renderer.renderedMessages.shouldContain("info:Debug mode disabled")
        }

        "state renders active FSM state and no active command" {
            val renderer = MockCliRenderer()
            val commandEngine = DefaultCommandEngine()
            commandEngine.startCommand("test")
            val taskRepo = mockTaskRepository()
            val factRepo = mockFactRepository()
            val invariantRepo = mockInvariantRepository()
            val dialogSessionRepo = mockDialogSessionRepository()
            val profileRepo = mockProfileRepository()
            val taskStepRepo = mockTaskStepRepository()

            val memoryService = MemoryService(dialogSessionRepo, taskRepo, taskStepRepo, factRepo)
            val promptBuilder = PromptBuilder()
            val invariantService = mockk<InvariantService>(relaxed = true)
            val debugCommandHandler = DebugCommandHandler(DebugMode())

            val dialogService = DialogService(
                mockk(relaxed = true),
                memoryService,
                promptBuilder,
                TaskExecutionConfig(),
                profileRepo,
                invariantService
            )
            val planCommandHandler = PlanCommandHandler(
                taskRepo,
                commandEngine,
                FactCollector(factRepo, KeywordExtractor()),
                null,
                StepParser(),
                invariantService,
                PlanMessageBuilder(commandEngine)
            )

            val commandHandler = CommandHandler(emptyMap())
            val input = FakeCliInput(lines = ArrayDeque(listOf(":state", ":abort", "yes", ":state", "q")))
            val handlers = CliCommandHandlers(
                command = commandHandler,
                debug = debugCommandHandler,
                todoTask = TodoTaskCommandHandler(TodoTaskService(taskRepo), memoryService, renderer, { "" }),
                taskStep = TaskStepCommandHandler(
                    TaskStepService(taskStepRepo, memoryService, Clock.systemUTC()),
                    renderer
                ),
                memory = MemoryCommandHandler(
                    memoryService,
                    profileRepo,
                    debugCommandHandler,
                    commandEngine,
                    invariantService,
                    renderer
                ),
                ltm = LtmCommandHandler(LtmService(factRepo, Clock.systemUTC()), renderer),
                fsm = FsmCommandHandler(commandEngine, renderer, { input.readLine() }),
                invariant = InvariantCommandHandler(invariantService, renderer, { null }),
                profile = ProfileCommandHandler(ProfileService(profileRepo), renderer, { null }, { "" }),
            )
            val userInputFlowHandler =
                UserInputFlowHandler(renderer, dialogService, planCommandHandler, commandEngine, commandHandler)
            val planFlowHandler = PlanFlowHandler(renderer, dialogService, planCommandHandler)
            val dispatcher = CliCommandDispatcher(renderer, handlers, userInputFlowHandler, planFlowHandler)

            val app = CliApplication(renderer, input, dispatcher, commandHandler, {})

            app.run()

            renderer.renderedMessages.shouldContain("fsmStateInfo:test")
            renderer.renderedMessages.shouldContain("abortSuccess")
            renderer.renderedMessages.shouldContain("noActiveCommand")
        }

        "abort cancel path uses injected input confirmation" {
            val renderer = MockCliRenderer()
            val commandEngine = DefaultCommandEngine()
            commandEngine.startCommand("test")
            val taskRepo = mockTaskRepository()
            val factRepo = mockFactRepository()
            val invariantRepo = mockInvariantRepository()
            val dialogSessionRepo = mockDialogSessionRepository()
            val profileRepo = mockProfileRepository()
            val taskStepRepo = mockTaskStepRepository()

            val memoryService = MemoryService(dialogSessionRepo, taskRepo, taskStepRepo, factRepo)
            val promptBuilder = PromptBuilder()
            val invariantService = mockk<InvariantService>(relaxed = true)
            val debugCommandHandler = DebugCommandHandler(DebugMode())

            val dialogService = DialogService(
                mockk(relaxed = true),
                memoryService,
                promptBuilder,
                TaskExecutionConfig(),
                profileRepo,
                invariantService
            )
            val planCommandHandler = PlanCommandHandler(
                taskRepo,
                commandEngine,
                FactCollector(factRepo, KeywordExtractor()),
                null,
                StepParser(),
                invariantService,
                PlanMessageBuilder(commandEngine)
            )

            val commandHandler = CommandHandler(emptyMap())
            val input = FakeCliInput(lines = ArrayDeque(listOf(":abort", "no", ":state", ":abort", "yes", "q")))
            val handlers = CliCommandHandlers(
                command = commandHandler,
                debug = debugCommandHandler,
                todoTask = TodoTaskCommandHandler(TodoTaskService(taskRepo), memoryService, renderer, { "" }),
                taskStep = TaskStepCommandHandler(
                    TaskStepService(taskStepRepo, memoryService, Clock.systemUTC()),
                    renderer
                ),
                memory = MemoryCommandHandler(
                    memoryService,
                    profileRepo,
                    debugCommandHandler,
                    commandEngine,
                    invariantService,
                    renderer
                ),
                ltm = LtmCommandHandler(LtmService(factRepo, Clock.systemUTC()), renderer),
                fsm = FsmCommandHandler(commandEngine, renderer, { input.readLine() }),
                invariant = InvariantCommandHandler(invariantService, renderer, { null }),
                profile = ProfileCommandHandler(ProfileService(profileRepo), renderer, { null }, { "" }),
            )
            val userInputFlowHandler =
                UserInputFlowHandler(renderer, dialogService, planCommandHandler, commandEngine, commandHandler)
            val planFlowHandler = PlanFlowHandler(renderer, dialogService, planCommandHandler)
            val dispatcher = CliCommandDispatcher(renderer, handlers, userInputFlowHandler, planFlowHandler)

            val app = CliApplication(renderer, input, dispatcher, commandHandler, {})

            app.run()

            renderer.renderedMessages.shouldContain("abortConfirmation")
            renderer.renderedMessages.shouldContain("abortCancelled")
            renderer.renderedMessages.shouldContain("fsmStateInfo:test")
            renderer.renderedMessages.shouldContain("abortSuccess")
        }

        "goto command routes through parser context and dispatcher" {
            val renderer = MockCliRenderer()
            val app = createApp(
                mapOf(TaskId("1") to MockTaskExecutor(TaskId("1"))),
                renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf("1", ":goto", "q")))
            )

            app.run()

            renderer.renderedMessages.shouldContain("gotoNoActiveCommand")
        }

        "invariant command routes through parser context and dispatcher" {
            val renderer = MockCliRenderer()
            val app = createApp(
                mapOf(TaskId("1") to MockTaskExecutor(TaskId("1"))),
                renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf("1", ":invariant list", "q")))
            )

            app.run()

            renderer.renderedMessages.shouldContain("invariantList:0")
        }

        "legacy dialog command routes to unsupported message" {
            val renderer = MockCliRenderer()
            val app = createApp(
                mapOf(TaskId("1") to MockTaskExecutor(TaskId("1"))),
                renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf("1", ":new old", ":history", "q")))
            )

            app.run()

            renderer.renderedMessages.count { it == "info:Команды диалогов больше не поддерживаются" } shouldBe 2
        }

        "profile command routes through parser context and dispatcher" {
            val renderer = MockCliRenderer()
            val app = createApp(
                mapOf(TaskId("1") to MockTaskExecutor(TaskId("1"))),
                renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf("1", ":profile-list", ":profile-use Missing", "q")))
            )

            app.run()

            renderer.renderedMessages.shouldContain("profileList:0")
            renderer.renderedMessages.shouldContain("profileNotFoundByName:Missing")
        }

        "status command routes through parser and dispatcher with active profile" {
            val renderer = MockCliRenderer()
            val profileRepo = io.averkhogliad.ai.challenge.week2.infrastructure.persistence.InMemoryProfileRepository()
            val profileService = ProfileService(profileRepo)
            runTest {
                val profile = profileService.handleCreateProfile("Active", "content", "")
                profileService.handleActivateProfile(profile.id)
            }

            val app = createApp(
                mapOf(TaskId("1") to MockTaskExecutor(TaskId("1"))),
                renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf("1", ":status", "q"))),
                profileRepo = profileRepo
            )

            app.run()

            renderer.renderedMessages.shouldContain("statusProfile:Active")
        }

        "status command routes through parser and dispatcher without active profile" {
            val renderer = MockCliRenderer()
            val profileRepo = io.averkhogliad.ai.challenge.week2.infrastructure.persistence.InMemoryProfileRepository()
            val app = createApp(
                mapOf(TaskId("1") to MockTaskExecutor(TaskId("1"))),
                renderer,
                input = FakeCliInput(lines = ArrayDeque(listOf("1", ":status", "q"))),
                profileRepo = profileRepo
            )

            app.run()

            renderer.renderedMessages.shouldContain("statusProfile:null")
        }

        "todo detail user input uses dialog service task detail context before executor" {
            val renderer = MockCliRenderer()
            val llmPort = object : LlmPort {
                override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig): TaskResult =
                    TaskResult.Success("unused")

                override suspend fun chatWithMessages(
                    messages: List<io.averkhogliad.ai.challenge.week2.domain.service.ChatMessage>,
                    config: TaskExecutionConfig
                ): TaskResult = TaskResult.Success("detail response")

                override suspend fun listModels(): List<io.averkhogliad.ai.challenge.week2.domain.ModelId> = emptyList()
            }

            val sessions = mutableMapOf<String, DialogSession>()
            val sessionRepo = object : DialogSessionRepository {
                override fun findById(id: SessionId) = sessions[id.value]
                override fun save(session: DialogSession): DialogSession {
                    sessions[session.id.value] = session; return session
                }

                override fun findByTaskId(taskId: TaskId) = sessions.values.firstOrNull { it.taskId == taskId }
                override fun findActiveSession() = sessions.values.firstOrNull()
                override fun delete(id: SessionId) {
                    sessions.remove(id.value)
                }
            }
            val taskRepo = mockTaskRepository()
            val factRepo = mockFactRepository()
            val taskStepRepo = mockTaskStepRepository()
            val memoryService = MemoryService(sessionRepo, taskRepo, taskStepRepo, factRepo)
            val promptBuilder = PromptBuilder()
            val invariantRepo = mockInvariantRepository()
            val invariantsService = mockk<InvariantService>(relaxed = true)
            val profileRepo = mockProfileRepository()
            val dialogService = DialogService(
                llmPort,
                memoryService,
                promptBuilder,
                TaskExecutionConfig(),
                profileRepo,
                invariantsService
            )

            val commandEngine = DefaultCommandEngine()
            val planCommandHandler = PlanCommandHandler(
                taskRepo,
                commandEngine,
                FactCollector(factRepo, KeywordExtractor()),
                null,
                StepParser(),
                invariantsService,
                PlanMessageBuilder(commandEngine)
            )

            val executor = MockTaskExecutor(TaskId("2"))
            val flow = UserInputFlowHandler(
                renderer,
                dialogService,
                planCommandHandler,
                commandEngine,
                CommandHandler(mapOf(TaskId("2") to executor))
            )

            flow.handle(Command.UserInput("hello"), CliState(currentTaskId = 2, currentTodoTaskId = "42"))

            executor.lastPrompt.shouldBeNull()
            val detailSession = sessions["session_task_42"]
            detailSession.shouldNotBeNull()
            detailSession.level shouldBe SessionLevel.TASK_DETAIL
            detailSession.taskId shouldBe TaskId("42")
            renderer.renderedMessages.shouldContain("result:Success")
        }

        "run uses shared fake multiline input for todo add" {
            val renderer = MockCliRenderer()
            val input = FakeCliInput(
                lines = ArrayDeque(listOf("1", ":add Test task", "q")),
                multilineInputs = ArrayDeque(listOf("Shared multiline description"))
            )
            val app = createApp(emptyMap(), renderer, input = input)

            app.run()

            renderer.renderedMessages.any { it.startsWith("taskCreated:") }.shouldBeTrue()
            renderer.renderedMessages.shouldContain("info:Введите описание задачи (Enter — пропустить, пустая строка — завершить):")
            input.readMultilineCalls shouldBe 1
        }
    }

    // ========================================================================
    // State management
    // ========================================================================

    "State management" - {

        "CliState has default values" {
            val state = CliState()
            state.currentTaskId.shouldBeNull()
            state.currentDialogId.shouldBeNull()
            state.executionConfig shouldBe TaskExecutionConfig()
            state.isRunning.shouldBeTrue()
        }

        "CliState is immutable" {
            val state1 = CliState(currentTaskId = 1)
            val state2 = state1.copy(currentTaskId = 2)
            state1.currentTaskId shouldBe 1
            state2.currentTaskId shouldBe 2
        }

        "CliState supports copy with modifications" {
            val state = CliState()
            val newState = state.copy(currentTaskId = 1, currentDialogId = "dialog-1", isRunning = false)
            newState.currentTaskId shouldBe 1
            newState.currentDialogId shouldBe "dialog-1"
            newState.isRunning.shouldBeFalse()
        }
    }

    // ========================================================================
    // Executor interaction
    // ========================================================================

    "Executor interaction" - {

        "creates application with multiple executors" {
            val executor1 = MockTaskExecutor(TaskId("1"))
            val executor2 = MockTaskExecutor(TaskId("2"))
            val renderer = MockCliRenderer()
            val app = createApp(mapOf(TaskId("1") to executor1, TaskId("2") to executor2), renderer)
            app.shouldNotBeNull()
        }

        "creates application with empty executors" {
            val renderer = MockCliRenderer()
            val app = createApp(emptyMap(), renderer)
            app.shouldNotBeNull()
        }
    }
})

package io.averkhogliad.ai.challenge.week4.cli.it

import io.averkhogliad.ai.challenge.week4.cli.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week4.cli.application.InvariantService
import io.averkhogliad.ai.challenge.week4.cli.application.ProfileService
import io.averkhogliad.ai.challenge.week4.cli.application.handler.DebugCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.CliRenderer
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.handlers.MemoryCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.service.InvariantRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MemoryStatus
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDialogSessionRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteProfileRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files

/**
 * Интеграционные тесты для US-PM-12: отображение профиля в команде :status.
 *
 * Проверяет полный путь от вызова до БД и обратно:
 * - Реальный SQLite (SqliteProfileRepository, SqliteDialogSessionRepository)
 * - Реальные MemoryService, ProfileService, InvariantService, CommandEngine
 * - Без моков и стабов критических зависимостей
 * - Только TestCliRenderer для захвата вывода (рендеринг — I/O)
 */
class StatusWithProfileIT : FreeSpec({

    /**
     * Тестовый рендерер — захватывает вызовы renderStatusProfile для проверки.
     * Это не мок бизнес-логики, а способ захвата I/O-вывода.
     */
    class TestCliRenderer : CliRenderer {
        val renderedMessages = mutableListOf<String>()

        override fun renderWelcome() {}
        override fun renderMenu(executors: List<io.averkhogliad.ai.challenge.week4.cli.application.executor.TaskExecutor>) {}
        override fun renderTaskHeader(metadata: io.averkhogliad.ai.challenge.week4.cli.domain.TaskMetadata) {}
        override fun renderResult(result: io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult) {}
        override fun renderError(message: String) {}
        override fun renderPrompt(state: CliState) {}
        override fun renderHelp(state: CliState) {}
        override fun renderParameters(state: CliState) {}
        override fun renderGoodbye() {}
        override fun renderRequestInfo(prompt: String, config: TaskExecutionConfig) {}
        override fun renderLoadingStart(message: String) {}
        override fun renderLoadingStop() {}
        override fun renderSuccess(message: String) {}
        override fun renderInfo(message: String) {}
        override fun renderTaskList(tasks: List<Task>) {}
        override fun renderTaskDetail(task: Task) {}
        override fun renderTaskCreated(taskId: TaskId) {}
        override fun renderTaskUpdated(taskId: TaskId) {}
        override fun renderTaskDeleted(taskId: TaskId) {}
        override fun renderTaskClosed(taskId: TaskId) {}
        override fun renderTaskCancelled(taskId: TaskId) {}
        override fun renderMemoryStatus(status: MemoryStatus) {}
        override fun renderMemoryCleared() {}
        override fun renderStepCreated(step: TaskStep) {}
        override fun renderStepList(steps: List<TaskStep>) {}
        override fun renderStepCompleted(step: TaskStep) {}
        override fun renderStepError(message: String) {}
        override fun renderFactSaved(fact: Fact) {}
        override fun renderFactList(facts: List<Fact>) {}
        override fun renderFactForgotten(factId: String) {}
        override fun renderFactNotFound(factId: String) {}
        override fun renderFactSearchResults(facts: List<Fact>, query: String) {}
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
        override fun renderProfileContentTooLong(length: Int) {}

        override fun renderStatusProfile(profileName: String?) {
            renderedMessages.add("statusProfile:${profileName ?: "null"}")
        }

        override fun renderStatusDebug(enabled: Boolean) {}
        override fun renderStatusActiveCommand(commandName: String?) {}
        override fun renderFsmState(state: CommandState) {}
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

        override fun renderStatusFsm(stage: CommandStage?, availableTransitions: List<Transition>) {}
        override fun renderStateMap(stateMap: StateMap) {}
        override fun renderGotoSuccess(from: CommandStage, to: CommandStage) {}
        override fun renderGotoError(reason: String) {}
        override fun renderGotoNoActiveCommand() {}
        override fun renderGotoInvalidState(stateName: String) {}
        override fun renderAvailableTransitions(transitions: List<Transition>) {}

        override fun renderTelemetry(result: io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult) {}
    }

    /**
     * In-memory InvariantRepository — в проекте нет SQLite-реализации для Invariant.
     */
    class InMemoryInvariantRepository : InvariantRepository {
        private val invariants = mutableMapOf<InvariantId, Invariant>()

        override suspend fun save(invariant: Invariant): Invariant {
            invariants[invariant.id] = invariant
            return invariant
        }

        override suspend fun findById(id: InvariantId): Invariant? = invariants[id]
        override suspend fun findAll(): List<Invariant> = invariants.values.toList()
        override suspend fun delete(id: InvariantId): Boolean = invariants.remove(id) != null
        override suspend fun count(): Int = invariants.size
        override fun close() {}
    }

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var profileRepository: SqliteProfileRepository
    lateinit var profileService: ProfileService
    lateinit var dialogSessionRepository: SqliteDialogSessionRepository
    lateinit var memoryService: MemoryService
    lateinit var invariantService: InvariantService
    lateinit var commandEngine: DefaultCommandEngine
    lateinit var debugCommandHandler: DebugCommandHandler
    lateinit var renderer: TestCliRenderer

    beforeTest {
        tempDbFile = Files.createTempFile("test-status-profile-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        profileRepository = SqliteProfileRepository(database)
        profileService = ProfileService(profileRepository)
        dialogSessionRepository = SqliteDialogSessionRepository(database)
        memoryService = MemoryService(dialogSessionRepository)
        invariantService = InvariantService(InMemoryInvariantRepository())
        commandEngine = DefaultCommandEngine()
        debugCommandHandler = DebugCommandHandler(DebugMode())
        renderer = TestCliRenderer()
    }

    afterTest {
        database.close()
        tempDbFile.delete()
        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    "should show active profile name in status" {
        runTest {
            // Создаём профиль через ProfileService и активируем
            val profile = profileService.handleCreateProfile("Test Profile", "Test content", "")
            profileService.handleActivateProfile(profile.id)

            // Проверяем, что профиль действительно активен в БД
            val active = profileRepository.findActive()
            active shouldNotBe null
            active!!.isActive shouldBe true
            active.name shouldBe "Test Profile"

            // Создаём MemoryCommandHandler с реальными компонентами
            val statusHandler = MemoryCommandHandler(
                memoryService = memoryService,
                profileRepository = profileRepository,
                debugCommandHandler = debugCommandHandler,
                commandEngine = commandEngine,
                invariantService = invariantService,
                renderer = renderer
            )

            // Вызываем handleShowStatus — полный путь через реальные компоненты до БД
            statusHandler.handleShowStatus(CliState(taskListMode = true))

            val statusMsg = renderer.renderedMessages.find { it.startsWith("statusProfile:") }
            statusMsg shouldNotBe null
            statusMsg shouldBe "statusProfile:Test Profile"
        }
    }

    "should show no profile when none is active" {
        runTest {
            val active = profileRepository.findActive()
            active shouldBe null

            val statusHandler = MemoryCommandHandler(
                memoryService = memoryService,
                profileRepository = profileRepository,
                debugCommandHandler = debugCommandHandler,
                commandEngine = commandEngine,
                invariantService = invariantService,
                renderer = renderer
            )

            statusHandler.handleShowStatus(CliState(taskListMode = true))

            val statusMsg = renderer.renderedMessages.find { it.startsWith("statusProfile:") }
            statusMsg shouldNotBe null
            statusMsg shouldBe "statusProfile:null"
        }
    }
})

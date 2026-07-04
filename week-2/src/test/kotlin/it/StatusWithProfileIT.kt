package io.averkhogliad.ai.challenge.week2.it

import io.averkhogliad.ai.challenge.week2.application.DefaultCommandEngine
import io.averkhogliad.ai.challenge.week2.application.InvariantService
import io.averkhogliad.ai.challenge.week2.application.ProfileService
import io.averkhogliad.ai.challenge.week2.application.handler.DebugCommandHandler
import io.averkhogliad.ai.challenge.week2.cli.CliRenderer
import io.averkhogliad.ai.challenge.week2.cli.CliState
import io.averkhogliad.ai.challenge.week2.cli.handlers.MemoryCommandHandler
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.*
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryStatus
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.*
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import java.io.File
import java.nio.file.Files

/**
 * Интеграционные тесты для US-PM-12: отображение профиля в команде :status.
 *
 * Проверяет сквозной путь через production-реализации:
 * - [SqliteProfileRepository] — настоящая БД для профилей
 * - [SqliteDialogSessionRepository] — настоящая БД для сессий
 * - [SqliteInvariantRepository] — настоящая БД для инвариантов
 * - [DefaultCommandEngine] — production-движок команд
 * - [DebugCommandHandler] — production-обработчик дебага
 */
class StatusWithProfileIT : FreeSpec({

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var profileRepository: SqliteProfileRepository
    lateinit var profileService: ProfileService
    lateinit var sessionRepository: SqliteDialogSessionRepository
    lateinit var memoryService: MemoryService
    lateinit var invariantService: InvariantService
    lateinit var commandEngine: DefaultCommandEngine
    lateinit var debugCommandHandler: DebugCommandHandler

    beforeEach {
        tempDbFile = Files.createTempFile("test-status-profile-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        profileRepository = SqliteProfileRepository(database)
        profileService = ProfileService(profileRepository)
        sessionRepository = SqliteDialogSessionRepository(database)
        val taskRepo = SqliteTaskRepository(database)
        val stepRepo = SqliteTaskStepRepository(database)
        val factRepo = SqliteFactRepository(database)
        memoryService = MemoryService(sessionRepository, taskRepo, stepRepo, factRepo)
        invariantService = InvariantService(SqliteInvariantRepository(database))
        commandEngine = DefaultCommandEngine()
        debugCommandHandler = DebugCommandHandler(DebugMode())
    }

    afterEach {
        database.close()
        tempDbFile.delete()
        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    "status command" - {

        "shows active profile name in status" {
            // given — тестовый рендерер, собирающий сообщения
            val renderer = TestStatusRenderer()

            // given — создаём и активируем профиль через production-сервис
            val profile = profileService.handleCreateProfile("Test Profile", "Test content", "")
            profileService.handleActivateProfile(profile.id)

            // given — production-хендлер
            val statusHandler = MemoryCommandHandler(
                memoryService = memoryService,
                profileRepository = profileRepository,
                debugCommandHandler = debugCommandHandler,
                commandEngine = commandEngine,
                invariantService = invariantService,
                renderer = renderer
            )

            // when
            statusHandler.handleShowStatus(CliState(taskListMode = true))

            // then — проверяем, что в статусе отобразилось имя профиля
            renderer.renderedMessages.shouldContain("statusProfile:Test Profile")
        }

        "shows no profile when none is active" {
            // given
            val renderer = TestStatusRenderer()

            // given — нет активного профиля
            profileRepository.findActive().shouldBeNull()

            val statusHandler = MemoryCommandHandler(
                memoryService = memoryService,
                profileRepository = profileRepository,
                debugCommandHandler = debugCommandHandler,
                commandEngine = commandEngine,
                invariantService = invariantService,
                renderer = renderer
            )

            // when
            statusHandler.handleShowStatus(CliState(taskListMode = true))

            // then
            renderer.renderedMessages.shouldContain("statusProfile:null")
        }
    }
})

/**
 * Минимальный тестовый рендерер, собирающий ключевые сообщения для проверки.
 */
private class TestStatusRenderer : CliRenderer {
    val renderedMessages = mutableListOf<String>()

    override fun renderStatusProfile(profileName: String?) {
        renderedMessages.add("statusProfile:${profileName ?: "null"}")
    }

    override fun renderMemoryStatus(status: MemoryStatus) {
        renderedMessages.add("memoryStatus")
    }

    override fun renderStatusDebug(enabled: Boolean) {
        renderedMessages.add("statusDebug:$enabled")
    }

    override fun renderStatusFsm(stage: CommandStage?, availableTransitions: List<Transition>) {
        renderedMessages.add("statusFsm:${stage?.name}:${availableTransitions.size}")
    }

    override fun renderStatusInvariants(count: Int) {
        renderedMessages.add("statusInvariants:$count")
    }

    // Остальные методы — no-op для этого теста
    override fun renderWelcome() {}
    override fun renderGoodbye() {}
    override fun renderMenu(executors: List<io.averkhogliad.ai.challenge.week2.application.executor.TaskExecutor>) {}
    override fun renderTaskHeader(metadata: io.averkhogliad.ai.challenge.week2.domain.TaskMetadata) {}
    override fun renderResult(result: io.averkhogliad.ai.challenge.week2.domain.TaskResult) {}
    override fun renderError(message: String) {}
    override fun renderPrompt(state: CliState) {}
    override fun renderHelp(state: CliState) {}
    override fun renderParameters(state: CliState) {}
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
    override fun renderFsmState(state: CommandState) {}
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
    override fun waitForEnter() {}
    override fun renderStateMap(stateMap: StateMap) {}
    override fun renderGotoSuccess(from: CommandStage, to: CommandStage) {}
    override fun renderGotoError(reason: String) {}
    override fun renderGotoNoActiveCommand() {}
    override fun renderGotoInvalidState(stateName: String) {}
    override fun renderAvailableTransitions(transitions: List<Transition>) {}
    override fun renderTelemetry(result: io.averkhogliad.ai.challenge.week2.domain.TaskResult) {}
}

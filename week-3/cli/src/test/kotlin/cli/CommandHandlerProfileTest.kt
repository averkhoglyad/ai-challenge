package io.averkhogliad.ai.challenge.week3.cli.cli

import io.averkhogliad.ai.challenge.week3.cli.application.ProfileService
import io.averkhogliad.ai.challenge.week3.cli.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week3.cli.cli.handlers.ProfileCommandHandler
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week3.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
import io.averkhogliad.ai.challenge.week3.cli.domain.service.MemoryStatus
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.InMemoryProfileRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandHandlerProfileTest {

    @Test
    fun `CommandHandler does not handle ProfileUse`() = runBlocking {
        val repo = InMemoryProfileRepository()
        val profileService = ProfileService(repo)
        profileService.handleCreateProfile("Test Profile", "Test content", "")
        val handler = CommandHandler(emptyMap())
        val state = CliState()

        val result = handler.handle(Command.ProfileUse("Test Profile"), state)

        assertNull(repo.findActive())
        assertEquals(state, result)
    }

    @Test
    fun `ProfileCommandHandler activates profile through ProfileService directly`() = runBlocking {
        val repo = InMemoryProfileRepository()
        val profileService = ProfileService(repo)
        profileService.handleCreateProfile("Test Profile", "Test content", "")
        val renderer = RecordingProfileRenderer()
        val handler = ProfileCommandHandler(profileService, renderer, readMultiline = { "" })
        val state = CliState()

        val result = handler.handleProfileUse(Command.ProfileUse("Test Profile"), state)

        val activated = repo.findByName("Test Profile")
        assertNotNull(activated)
        assertTrue(activated.isActive)
        assertEquals("Test Profile", renderer.profileDetailName)
        assertEquals(state, result)
    }

    @Test
    fun `ProfileCommandHandler deactivates profile through ProfileService directly`() = runBlocking {
        val repo = InMemoryProfileRepository()
        val profileService = ProfileService(repo)
        profileService.handleCreateProfile("Test Profile", "Test content", "")
        profileService.handleActivateByName("Test Profile")
        val renderer = RecordingProfileRenderer()
        val handler = ProfileCommandHandler(profileService, renderer, readMultiline = { "" })

        val result = handler.handleProfileUse(Command.ProfileUse("none"), CliState())

        assertNull(repo.findActive())
        assertEquals("Профиль деактивирован", renderer.infoMessage)
        assertEquals(CliState(), result)
    }

    @Test
    fun `ProfileCommandHandler renders not found for nonexistent profile`() = runBlocking {
        val repo = InMemoryProfileRepository()
        val profileService = ProfileService(repo)
        val renderer = RecordingProfileRenderer()
        val handler = ProfileCommandHandler(profileService, renderer, readMultiline = { "" })

        val result = handler.handleProfileUse(Command.ProfileUse("Missing Profile"), CliState())

        assertEquals("Missing Profile", renderer.profileNotFoundName)
        assertEquals(CliState(), result)
    }

    private class RecordingProfileRenderer : CliRenderer {
        var profileDetailName: String? = null
        var infoMessage: String? = null
        var profileNotFoundName: String? = null


        override fun renderProfileDetail(profile: Profile) {
            profileDetailName = profile.name
        }

        override fun renderInfo(message: String) {
            infoMessage = message
        }

        override fun renderProfileList(profiles: List<Profile>) {}
        override fun renderProfileDeleted(name: String) {}
        override fun renderProfileUpdated(name: String) {}
        override fun renderProfileError(message: String) {}
        override fun renderProfileNotFoundById(id: String) {}
        override fun renderProfileNotFoundByName(name: String) {
            profileNotFoundName = name
        }

        override fun renderProfileAlreadyExists(name: String) {}
        override fun renderMissingProfileId() {}
        override fun renderMissingProfileName() {}
        override fun renderEmptyProfileContent() {}
        override fun renderCannotDeleteActiveProfile() {}
        override fun renderProfileContentTooLong(length: Int) {}
        override fun renderProfileDescriptionPrompt() {}
        override fun renderProfileInstructionsPrompt() {}
        override fun renderError(message: String) {}
        override fun renderSuccess(message: String) {}
        override fun renderMenu(executors: List<TaskExecutor>) {}
        override fun renderTaskHeader(metadata: TaskMetadata) {}
        override fun renderResult(result: TaskResult) {}
        override fun renderPrompt(state: CliState) {}
        override fun renderHelp(state: CliState) {}
        override fun renderParameters(state: CliState) {}
        override fun renderWelcome() {}
        override fun renderGoodbye() {}
        override fun renderRequestInfo(prompt: String, config: TaskExecutionConfig) {}
        override fun renderLoadingStart(message: String) {}
        override fun renderLoadingStop() {}
        override fun renderTaskList(tasks: List<Task>) {}
        override fun renderTaskDetail(task: Task) {}
        override fun renderTaskCreated(taskId: TaskId) {}
        override fun renderTaskUpdated(taskId: TaskId) {}
        override fun renderTaskDeleted(taskId: TaskId) {}
        override fun renderTaskClosed(taskId: TaskId) {}
        override fun renderTaskCancelled(taskId: TaskId) {}
        override fun renderStepCreated(step: TaskStep) {}
        override fun renderStepList(steps: List<TaskStep>) {}
        override fun renderStepCompleted(step: TaskStep) {}
        override fun renderStepError(message: String) {}
        override fun renderMemoryStatus(status: MemoryStatus) {}
        override fun renderMemoryCleared() {}
        override fun renderFactSaved(fact: Fact) {}
        override fun renderFactList(facts: List<Fact>) {}
        override fun renderFactForgotten(factId: String) {}
        override fun renderFactNotFound(factId: String) {}
        override fun renderFactSearchResults(facts: List<Fact>, query: String) {}
        override fun renderFactSearchEmpty(query: String) {}
        override fun renderMultilineInputPrompt() {}
        override fun renderStatusProfile(profileName: String?) {}
        override fun renderStatusDebug(enabled: Boolean) {}
        override fun renderStatusActiveCommand(commandName: String?) {}
        override fun renderFsmState(state: CommandState) {}
        override fun waitForEnter() {}
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
        override fun renderStatusFsm(stage: CommandStage?, availableTransitions: List<Transition>) {}
        override fun renderStateMap(stateMap: StateMap) {}
        override fun renderGotoSuccess(from: CommandStage, to: CommandStage) {}
        override fun renderGotoError(reason: String) {}
        override fun renderGotoNoActiveCommand() {}
        override fun renderGotoInvalidState(stateName: String) {}
        override fun renderAvailableTransitions(transitions: List<Transition>) {}
        override fun renderTelemetry(result: TaskResult) {}
    }
}

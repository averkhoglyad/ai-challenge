package io.averkhogliad.ai.challenge.week4.cli.unit.cli

import io.averkhogliad.ai.challenge.week4.cli.application.ProfileService
import io.averkhogliad.ai.challenge.week4.cli.cli.CliRenderer
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.cli.handlers.CommandHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.handlers.ProfileCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MemoryStatus
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.InMemoryProfileRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

class CommandHandlerProfileTest : FreeSpec({

    "CommandHandler (base)" - {
        "does not handle ProfileUse" {
            runTest {
                // given
                val repo = InMemoryProfileRepository()
                val profileService = ProfileService(repo)
                profileService.handleCreateProfile("Test Profile", "Test content", "")
                val handler = CommandHandler(emptyMap())
                val state = CliState()

                // when
                val result = handler.handle(Command.ProfileUse("Test Profile"), state)

                // then
                repo.findActive() shouldBe null
                result shouldBe state
            }
        }
    }

    "ProfileCommandHandler" - {
        "activates profile through ProfileService directly" {
            runTest {
                // given
                val repo = InMemoryProfileRepository()
                val profileService = ProfileService(repo)
                profileService.handleCreateProfile("Test Profile", "Test content", "")
                val renderer = RecordingProfileRenderer()
                val handler = ProfileCommandHandler(profileService, renderer, readMultiline = { "" })
                val state = CliState()

                // when
                val result = handler.handleProfileUse(Command.ProfileUse("Test Profile"), state)

                // then
                val activated = repo.findByName("Test Profile")
                (activated != null) shouldBe true
                activated!!.isActive shouldBe true
                renderer.profileDetailName shouldBe "Test Profile"
                result shouldBe state
            }
        }

        "deactivates profile through ProfileService directly" {
            runTest {
                // given
                val repo = InMemoryProfileRepository()
                val profileService = ProfileService(repo)
                profileService.handleCreateProfile("Test Profile", "Test content", "")
                profileService.handleActivateByName("Test Profile")
                val renderer = RecordingProfileRenderer()
                val handler = ProfileCommandHandler(profileService, renderer, readMultiline = { "" })

                // when
                val result = handler.handleProfileUse(Command.ProfileUse("none"), CliState())

                // then
                repo.findActive() shouldBe null
                renderer.infoMessage shouldBe "Профиль деактивирован"
                result shouldBe CliState()
            }
        }

        "renders not found for nonexistent profile" {
            runTest {
                // given
                val repo = InMemoryProfileRepository()
                val profileService = ProfileService(repo)
                val renderer = RecordingProfileRenderer()
                val handler = ProfileCommandHandler(profileService, renderer, readMultiline = { "" })

                // when
                val result = handler.handleProfileUse(Command.ProfileUse("Missing Profile"), CliState())

                // then
                renderer.profileNotFoundName shouldBe "Missing Profile"
                result shouldBe CliState()
            }
        }
    }
})

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
    override fun renderTaskHeader(metadata: TaskMetadata) {}
    override fun renderResult(result: TaskResult) {}
    override fun renderPrompt(state: CliState) {}
    override fun renderHelp(state: CliState) {}
    override fun renderParameters(state: CliState) {}
    override fun renderWelcome() {}
    override fun renderMenu(executors: List<io.averkhogliad.ai.challenge.week4.cli.application.executor.TaskExecutor>) {}
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

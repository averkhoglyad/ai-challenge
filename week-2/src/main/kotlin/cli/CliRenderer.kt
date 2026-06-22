package io.averkhogliad.ai.challenge.week2.cli

import io.averkhogliad.ai.challenge.week2.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week2.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.Fact
import io.averkhogliad.ai.challenge.week2.domain.model.Task
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryStatus

interface CliRenderer {
    fun renderMenu(executors: List<TaskExecutor>)
    fun renderTaskHeader(metadata: TaskMetadata)
    fun renderResult(result: TaskResult)
    fun renderError(message: String)
    fun renderPrompt(state: CliState)
    fun renderHelp(state: CliState)
    fun renderParameters(state: CliState)
    fun renderWelcome()
    fun renderGoodbye()
    fun renderRequestInfo(prompt: String, config: TaskExecutionConfig)
    fun renderLoadingStart(message: String)
    fun renderLoadingStop()

    // Dialog rendering methods (no-op — dialog functionality removed)
    fun renderSuccess(message: String)
    fun renderInfo(message: String)

    // Todo-manager rendering methods
    fun renderTaskList(tasks: List<Task>)
    fun renderTaskDetail(task: Task)
    fun renderTaskCreated(taskId: TaskId)
    fun renderTaskUpdated(taskId: TaskId)
    fun renderTaskDeleted(taskId: TaskId)
    fun renderTaskClosed(taskId: TaskId)
    fun renderTaskCancelled(taskId: TaskId)

    // Step management rendering methods
    fun renderStepCreated(step: TaskStep)
    fun renderStepList(steps: List<TaskStep>)
    fun renderStepCompleted(step: TaskStep)
    fun renderStepError(message: String)

    // Memory management rendering methods
    fun renderMemoryStatus(status: MemoryStatus)
    fun renderMemoryCleared()

    // LTM (Long-Term Memory) rendering methods
    fun renderFactSaved(fact: Fact)
    fun renderFactList(facts: List<Fact>)
    fun renderFactForgotten(factId: String)
    fun renderFactNotFound(factId: String)
    fun renderFactSearchResults(facts: List<Fact>, query: String)
    fun renderFactSearchEmpty(query: String)

    // Profile rendering methods
    fun renderProfileList(profiles: List<io.averkhogliad.ai.challenge.week2.domain.model.Profile>)
    fun renderProfileDetail(profile: io.averkhogliad.ai.challenge.week2.domain.model.Profile)
    fun renderProfileDeleted(name: String)
    fun renderProfileUpdated(name: String)
    fun renderProfileError(message: String)
    fun renderMultilineInputPrompt()

    // Profile creation step prompts (description + instructions)
    fun renderProfileDescriptionPrompt()
    fun renderProfileInstructionsPrompt()

    // Profile error rendering methods (специфичные сообщения)
    fun renderProfileNotFoundById(id: String)
    fun renderProfileNotFoundByName(name: String)
    fun renderProfileAlreadyExists(name: String)
    fun renderMissingProfileId()
    fun renderMissingProfileName()
    fun renderEmptyProfileContent()
    fun renderCannotDeleteActiveProfile()
    fun renderProfileContentTooLong(length: Int)

    // Profile status rendering in :status command
    fun renderStatusProfile(profileName: String?)

    // Debug mode status rendering in :status command
    fun renderStatusDebug(enabled: Boolean)

    // Active FSM command status rendering in :status command
    fun renderStatusActiveCommand(commandName: String?)

    // FSM (Finite State Machine) visualization for debug mode
    fun renderFsmState(state: io.averkhogliad.ai.challenge.week2.domain.model.CommandState)

    // Debug mode pause after step execution
    fun waitForEnter()

    // FSM state command rendering (:state)
    fun renderFsmStateInfo(state: io.averkhogliad.ai.challenge.week2.domain.model.CommandState)
    fun renderNoActiveCommand()

    // Abort command rendering (:abort)
    fun renderAbortConfirmation()
    fun renderAbortSuccess()
    fun renderAbortCancelled()
}

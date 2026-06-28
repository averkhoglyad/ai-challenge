package io.averkhogliad.ai.challenge.week3.cli.cli.handlers

import io.averkhogliad.ai.challenge.week3.cli.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week3.cli.application.service.TaskStepService
import io.averkhogliad.ai.challenge.week3.cli.cli.CliRenderer
import io.averkhogliad.ai.challenge.week3.cli.cli.CliState
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week3.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
import io.averkhogliad.ai.challenge.week3.cli.domain.service.DialogSessionRepository
import io.averkhogliad.ai.challenge.week3.cli.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week3.cli.domain.service.MemoryStatus
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskStepRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("TaskStepCommandHandler")
class TaskStepCommandHandlerTest {

    private lateinit var stepRepository: InMemoryTaskStepRepository
    private lateinit var memoryService: MemoryService
    private lateinit var renderer: RecordingRenderer
    private lateinit var handler: TaskStepCommandHandler

    @BeforeEach
    fun setUp() {
        stepRepository = InMemoryTaskStepRepository()
        memoryService = MemoryService(InMemoryDialogSessionRepository())
        renderer = RecordingRenderer()
        val taskStepService = TaskStepService(
            taskStepRepository = stepRepository,
            memoryService = memoryService
        )
        handler = TaskStepCommandHandler(
            taskStepService = taskStepService,
            renderer = renderer
        )
    }

    @Nested
    @DisplayName("step CRUD commands")
    inner class StepCrudCommands {

        @Test
        @DisplayName("AddStep creates step with open task")
        fun `AddStep creates step with open task`() = runBlocking {
            val taskId = TaskId("task-1")
            val state = CliState(currentTodoTaskId = taskId.value)

            handler.handleAddStep(Command.AddStep("Do something"), state)

            val steps = stepRepository.findByTaskId(taskId)
            assertEquals(1, steps.size)
            assertEquals("Do something", steps[0].text)
            assertFalse(steps[0].isCompleted)
            assertEquals(0, steps[0].order)
            assertEquals(taskId, steps[0].taskId)
            assertEquals(listOf("Step added"), renderer.infoMessages)
        }

        @Test
        @DisplayName("AddStep increments order for multiple steps")
        fun `AddStep increments order for multiple steps`() = runBlocking {
            val taskId = TaskId("task-1")
            val state = CliState(currentTodoTaskId = taskId.value)

            handler.handleAddStep(Command.AddStep("Step 1"), state)
            handler.handleAddStep(Command.AddStep("Step 2"), state)
            handler.handleAddStep(Command.AddStep("Step 3"), state)

            val steps = stepRepository.findByTaskId(taskId)
            assertEquals(3, steps.size)
            assertEquals(0, steps[0].order)
            assertEquals(1, steps[1].order)
            assertEquals(2, steps[2].order)
        }

        @Test
        @DisplayName("ListSteps renders steps for open task")
        fun `ListSteps renders steps for open task`() = runBlocking {
            val taskId = TaskId("task-1")
            val state = CliState(currentTodoTaskId = taskId.value)
            handler.handleAddStep(Command.AddStep("Step 1"), state)
            handler.handleAddStep(Command.AddStep("Step 2"), state)

            val newState = handler.handleListSteps(state)

            assertEquals(state, newState)
            assertTrue(renderer.errors.isEmpty())
            assertEquals(listOf("Step 1", "Step 2"), renderer.lastStepList.map { it.text })
        }


        @Test
        @DisplayName("CompleteStep marks step as completed")
        fun `CompleteStep marks step as completed`() = runBlocking {
            val taskId = TaskId("task-1")
            val state = CliState(currentTodoTaskId = taskId.value)
            handler.handleAddStep(Command.AddStep("To complete"), state)
            val stepId = stepRepository.findByTaskId(taskId).first().id.value

            handler.handleCompleteStep(Command.CompleteStep(stepId), state)

            val updatedStep = stepRepository.findById(TaskStepId(stepId))
            assertNotNull(updatedStep)
            assertTrue(updatedStep.isCompleted)
            assertEquals(listOf("Step added", "Step completed"), renderer.infoMessages)
        }
    }

    @Nested
    @DisplayName("validation")
    inner class Validation {

        @Test
        @DisplayName("AddStep without open task renders error")
        fun `AddStep without open task renders error`() = runBlocking {
            val state = CliState(currentTodoTaskId = null)

            val newState = handler.handleAddStep(Command.AddStep("test"), state)

            assertEquals(state, newState)
            assertEquals(listOf("No task is currently open"), renderer.errors)
        }

        @Test
        @DisplayName("ListSteps without open task renders error")
        fun `ListSteps without open task renders error`() = runBlocking {
            val state = CliState(currentTodoTaskId = null)

            val newState = handler.handleListSteps(state)

            assertEquals(state, newState)
            assertEquals(listOf("No task is currently open"), renderer.errors)
        }

        @Test
        @DisplayName("CompleteStep without open task renders error")
        fun `CompleteStep without open task renders error`() = runBlocking {
            val state = CliState(currentTodoTaskId = null)

            val newState = handler.handleCompleteStep(Command.CompleteStep("step-1"), state)

            assertEquals(state, newState)
            assertEquals(listOf("No task is currently open"), renderer.errors)
        }
    }

    private class InMemoryTaskStepRepository : TaskStepRepository {
        private val steps = mutableMapOf<TaskStepId, TaskStep>()

        override fun save(step: TaskStep): TaskStep {
            steps[step.id] = step
            return step
        }

        override fun findByTaskId(taskId: TaskId): List<TaskStep> =
            steps.values.filter { it.taskId == taskId }.sortedBy { it.order }

        override fun findById(stepId: TaskStepId): TaskStep? = steps[stepId]

        override fun delete(stepId: TaskStepId): Boolean = steps.remove(stepId) != null

        override fun deleteByTaskId(taskId: TaskId): Int {
            val idsToDelete = steps.values
                .filter { it.taskId == taskId }
                .map { it.id }
            idsToDelete.forEach(steps::remove)
            return idsToDelete.size
        }

        override fun countByTaskId(taskId: TaskId): Int =
            steps.values.count { it.taskId == taskId }
    }

    private class RecordingRenderer : CliRenderer {
        val errors = mutableListOf<String>()
        val infoMessages = mutableListOf<String>()
        var lastStepList: List<TaskStep> = emptyList()


        override fun renderMenu(executors: List<TaskExecutor>) = Unit
        override fun renderTaskHeader(metadata: TaskMetadata) = Unit
        override fun renderResult(result: TaskResult) = Unit
        override fun renderError(message: String) {
            errors.add(message)
        }

        override fun renderPrompt(state: CliState) = Unit
        override fun renderHelp(state: CliState) = Unit
        override fun renderParameters(state: CliState) = Unit
        override fun renderWelcome() = Unit
        override fun renderGoodbye() = Unit
        override fun renderRequestInfo(prompt: String, config: TaskExecutionConfig) = Unit
        override fun renderLoadingStart(message: String) = Unit
        override fun renderLoadingStop() = Unit
        override fun renderSuccess(message: String) = Unit
        override fun renderInfo(message: String) {
            infoMessages.add(message)
        }

        override fun renderTaskList(tasks: List<Task>) = Unit
        override fun renderTaskDetail(task: Task) = Unit
        override fun renderTaskCreated(taskId: TaskId) = Unit
        override fun renderTaskUpdated(taskId: TaskId) = Unit
        override fun renderTaskDeleted(taskId: TaskId) = Unit
        override fun renderTaskClosed(taskId: TaskId) = Unit
        override fun renderTaskCancelled(taskId: TaskId) = Unit
        override fun renderStepCreated(step: TaskStep) = Unit
        override fun renderStepList(steps: List<TaskStep>) {
            lastStepList = steps
        }

        override fun renderStepCompleted(step: TaskStep) = Unit
        override fun renderStepError(message: String) = Unit
        override fun renderMemoryStatus(status: MemoryStatus) = Unit
        override fun renderMemoryCleared() = Unit
        override fun renderFactSaved(fact: Fact) = Unit
        override fun renderFactList(facts: List<Fact>) = Unit
        override fun renderFactForgotten(factId: String) = Unit
        override fun renderFactNotFound(factId: String) = Unit
        override fun renderFactSearchResults(facts: List<Fact>, query: String) = Unit
        override fun renderFactSearchEmpty(query: String) = Unit
        override fun renderProfileList(profiles: List<Profile>) = Unit
        override fun renderProfileDetail(profile: Profile) = Unit
        override fun renderProfileDeleted(name: String) = Unit
        override fun renderProfileUpdated(name: String) = Unit
        override fun renderProfileError(message: String) = Unit
        override fun renderMultilineInputPrompt() = Unit
        override fun renderProfileDescriptionPrompt() = Unit
        override fun renderProfileInstructionsPrompt() = Unit
        override fun renderProfileNotFoundById(id: String) = Unit
        override fun renderProfileNotFoundByName(name: String) = Unit
        override fun renderProfileAlreadyExists(name: String) = Unit
        override fun renderMissingProfileId() = Unit
        override fun renderMissingProfileName() = Unit
        override fun renderEmptyProfileContent() = Unit
        override fun renderCannotDeleteActiveProfile() = Unit
        override fun renderProfileContentTooLong(length: Int) = Unit
        override fun renderStatusProfile(profileName: String?) = Unit
        override fun renderStatusDebug(enabled: Boolean) = Unit
        override fun renderStatusActiveCommand(commandName: String?) = Unit
        override fun renderFsmState(state: CommandState) = Unit
        override fun waitForEnter() = Unit
        override fun renderFsmStateInfo(state: CommandState) = Unit
        override fun renderNoActiveCommand() = Unit
        override fun renderAbortConfirmation() = Unit
        override fun renderAbortSuccess() = Unit
        override fun renderAbortCancelled() = Unit
        override fun renderInvariantList(invariants: List<Invariant>) = Unit
        override fun renderInvariantAdded(invariant: Invariant) = Unit
        override fun renderInvariantRemoved(id: Int) = Unit
        override fun renderInvariantNotFound(id: Int) = Unit
        override fun renderInvariantEmptyRule() = Unit
        override fun renderInvariantRemoveConfirmation(id: Int) = Unit
        override fun renderStatusInvariants(count: Int) = Unit
        override fun renderStatusFsm(stage: CommandStage?, availableTransitions: List<Transition>) = Unit
        override fun renderStateMap(stateMap: StateMap) = Unit
        override fun renderGotoSuccess(from: CommandStage, to: CommandStage) = Unit
        override fun renderGotoError(reason: String) = Unit
        override fun renderGotoNoActiveCommand() = Unit
        override fun renderGotoInvalidState(stateName: String) = Unit
        override fun renderAvailableTransitions(transitions: List<Transition>) = Unit
        override fun renderTelemetry(result: TaskResult) = Unit
    }

    private class InMemoryDialogSessionRepository : DialogSessionRepository {
        private val sessions = mutableMapOf<String, DialogSession>()

        override fun save(session: DialogSession): DialogSession {
            sessions[session.id.value] = session
            return session
        }

        override fun findById(id: SessionId): DialogSession? = sessions[id.value]

        override fun findByTaskId(taskId: TaskId): DialogSession? =
            sessions.values.firstOrNull { it.taskId == taskId }

        override fun findActiveSession(): DialogSession? = sessions.values.lastOrNull()

        override fun delete(id: SessionId) {
            sessions.remove(id.value)
        }
    }
}

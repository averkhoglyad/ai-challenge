package io.averkhogliad.ai.challenge.week4.cli.unit.cli.handlers

import io.averkhogliad.ai.challenge.week4.cli.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week4.cli.cli.CliRenderer
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week4.cli.cli.handlers.TodoTaskCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.service.DialogSessionRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MemoryStatus
import io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.util.*

class TodoTaskCommandHandlerTest : FreeSpec({

    lateinit var repository: InMemoryTaskRepository
    lateinit var todoTaskService: TodoTaskService
    lateinit var memoryService: MemoryService
    lateinit var renderer: RecordingRenderer
    lateinit var descriptionInput: MutableList<String>
    lateinit var handler: TodoTaskCommandHandler

    beforeEach {
        repository = InMemoryTaskRepository()
        todoTaskService = TodoTaskService(repository)
        memoryService = MemoryService(InMemoryDialogSessionRepository())
        renderer = RecordingRenderer()
        descriptionInput = mutableListOf()
        handler = TodoTaskCommandHandler(
            todoTaskService = todoTaskService,
            memoryService = memoryService,
            renderer = renderer,
            readMultiline = { descriptionInput.removeFirstOrNull() ?: EMPTY_INPUT }
        )
    }

    "task CRUD commands" - {
        "AddTask creates new task" {
            runTest {
                // given
                descriptionInput.add("Task description")

                // when
                handler.handleAddTask(Command.AddTask("Test Task"), CliState())

                // then
                val tasks = todoTaskService.listTasks()
                tasks.size shouldBe 1
                tasks[0].title shouldBe "Test Task"
                tasks[0].description shouldBe "Task description"
                renderer.createdTasks shouldBe listOf(tasks[0].id)
            }
        }

        "ListTasks renders all tasks" {
            runTest {
                // given
                todoTaskService.addTask("Task 1")
                todoTaskService.addTask("Task 2")

                // when
                handler.handleListTasks(CliState())

                // then
                renderer.lastTaskList.size shouldBe 2
            }
        }

        "OpenTask sets currentTodoTaskId in state" {
            runTest {
                // given
                val task = todoTaskService.addTask("Task to open")

                // when
                val newState = handler.handleOpenTask(Command.OpenTask(task.id), CliState())

                // then
                newState.currentTodoTaskId shouldBe task.id.value
                newState.taskListMode shouldBe false
                todoTaskService.currentTaskId shouldBe task.id
                val memoryStatus = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, task.id)
                memoryStatus.taskId shouldBe task.id
                renderer.lastTaskDetail shouldBe task
            }
        }

        "edit without id uses currentTaskId" {
            runTest {
                // given
                descriptionInput.add(EMPTY_INPUT)
                val task = todoTaskService.addTask("Original Title")
                handler.handleOpenTask(Command.OpenTask(task.id), CliState())

                // when
                handler.handleEditTask(
                    Command.EditTask(null, "Updated Title"),
                    CliState(currentTodoTaskId = task.id.value)
                )

                // then
                val updated = todoTaskService.listTasks().find { it.id == task.id }
                updated shouldNotBe null
                updated!!.title shouldBe "Updated Title"
                renderer.updatedTasks shouldBe listOf(task.id)
            }
        }

        "drop without id uses currentTaskId" {
            runTest {
                // given
                val task = todoTaskService.addTask("Task to drop")
                handler.handleOpenTask(Command.OpenTask(task.id), CliState())

                // when
                handler.handleDropTask(Command.DropTask(null), CliState(currentTodoTaskId = task.id.value))

                // then
                todoTaskService.listTasks().isEmpty() shouldBe true
                todoTaskService.currentTaskId shouldBe null
                renderer.deletedTasks shouldBe listOf(task.id)
            }
        }

        "close without id uses currentTaskId" {
            runTest {
                // given
                val task = todoTaskService.addTask("Task to close")
                handler.handleOpenTask(Command.OpenTask(task.id), CliState())

                // when
                val newState =
                    handler.handleCloseTask(Command.CloseTask(null), CliState(currentTodoTaskId = task.id.value))

                // then
                val closed = todoTaskService.listTasks().find { it.id == task.id }
                closed shouldNotBe null
                closed!!.status shouldBe TaskStatus.CLOSED
                todoTaskService.currentTaskId shouldBe null
                newState.currentTodoTaskId shouldBe null
                newState.taskListMode shouldBe true
                val memoryStatus = memoryService.getMemoryStatus(SessionLevel.TASK_LIST)
                memoryStatus.level shouldBe SessionLevel.TASK_LIST
                renderer.closedTasks shouldBe listOf(task.id)
            }
        }

        "cancel without id uses currentTaskId" {
            runTest {
                // given
                val task = todoTaskService.addTask("Task to cancel")
                handler.handleOpenTask(Command.OpenTask(task.id), CliState())

                // when
                val newState =
                    handler.handleCancelTask(Command.CancelTask(null), CliState(currentTodoTaskId = task.id.value))

                // then
                val cancelled = todoTaskService.listTasks().find { it.id == task.id }
                cancelled shouldNotBe null
                cancelled!!.status shouldBe TaskStatus.CANCELLED
                todoTaskService.currentTaskId shouldBe null
                newState.currentTodoTaskId shouldBe null
                newState.taskListMode shouldBe true
                val memoryStatus = memoryService.getMemoryStatus(SessionLevel.TASK_LIST)
                memoryStatus.level shouldBe SessionLevel.TASK_LIST
                renderer.cancelledTasks shouldBe listOf(task.id)
            }
        }

        "close explicit different task preserves current state and memory context" {
            runTest {
                // given
                val currentTask = todoTaskService.addTask("Current task")
                val otherTask = todoTaskService.addTask("Other task")
                val initialState = handler.handleOpenTask(Command.OpenTask(currentTask.id), CliState())

                // when
                val newState = handler.handleCloseTask(Command.CloseTask(otherTask.id), initialState)

                // then
                val closed = todoTaskService.listTasks().find { it.id == otherTask.id }
                closed shouldNotBe null
                closed!!.status shouldBe TaskStatus.CLOSED
                todoTaskService.currentTaskId shouldBe currentTask.id
                newState shouldBe initialState
                val memoryStatus = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, currentTask.id)
                memoryStatus.level shouldBe SessionLevel.TASK_DETAIL
                memoryStatus.taskId shouldBe currentTask.id
                renderer.closedTasks shouldBe listOf(otherTask.id)
            }
        }

        "cancel explicit different task preserves current state and memory context" {
            runTest {
                // given
                val currentTask = todoTaskService.addTask("Current task")
                val otherTask = todoTaskService.addTask("Other task")
                val initialState = handler.handleOpenTask(Command.OpenTask(currentTask.id), CliState())

                // when
                val newState = handler.handleCancelTask(Command.CancelTask(otherTask.id), initialState)

                // then
                val cancelled = todoTaskService.listTasks().find { it.id == otherTask.id }
                cancelled shouldNotBe null
                cancelled!!.status shouldBe TaskStatus.CANCELLED
                todoTaskService.currentTaskId shouldBe currentTask.id
                newState shouldBe initialState
                val memoryStatus = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, currentTask.id)
                memoryStatus.level shouldBe SessionLevel.TASK_DETAIL
                memoryStatus.taskId shouldBe currentTask.id
                renderer.cancelledTasks shouldBe listOf(otherTask.id)
            }
        }
    }

    "Back command" - {
        "Back from task detail clears currentTodoTaskId and renders task list" {
            runTest {
                // given
                todoTaskService.addTask("Task")

                // when
                val newState = handler.handleBack(CliState(currentTodoTaskId = "task-id")) {}

                // then
                newState.currentTodoTaskId shouldBe null
                newState.taskListMode shouldBe true
                val memoryStatus = memoryService.getMemoryStatus(SessionLevel.TASK_LIST)
                memoryStatus.level shouldBe SessionLevel.TASK_LIST
                renderer.lastTaskList.size shouldBe 1
            }
        }

        "Back from task list renders main menu" {
            runTest {
                // given
                var mainMenuRendered = false

                // when
                val newState = handler.handleBack(CliState(taskListMode = true)) {
                    mainMenuRendered = true
                }

                // then
                newState.currentTaskId shouldBe null
                newState.taskListMode shouldBe false
                val memoryStatus = memoryService.getMemoryStatus(SessionLevel.TASK_LIST)
                memoryStatus.level shouldBe SessionLevel.TASK_LIST
                mainMenuRendered shouldBe true
            }
        }
    }
}) {

    companion object {
        private const val EMPTY_INPUT = ""
    }
}

private class InMemoryTaskRepository : TaskRepository {
    private val tasks = linkedMapOf<String, Task>()

    override suspend fun save(task: Task) {
        tasks[task.id.value] = task
    }

    override suspend fun findById(id: TaskId): Task? = tasks[id.value]

    override suspend fun findAll(): List<Task> = tasks.values.toList()

    override suspend fun delete(id: TaskId) {
        tasks.remove(id.value)
    }

    override suspend fun exists(id: TaskId): Boolean = tasks.containsKey(id.value)

    override suspend fun saveSteps(taskId: TaskId, steps: List<TaskStep>) = Unit

    override suspend fun findStepsByTaskId(taskId: TaskId): List<TaskStep> = emptyList()

    override suspend fun updateEvent(taskId: TaskId, eventId: UUID, dueDate: LocalDate): Result<Unit> =
        Result.success(Unit)

    override suspend fun clearEvent(taskId: TaskId): Result<Unit> =
        Result.success(Unit)
}

private class InMemoryDialogSessionRepository : DialogSessionRepository {
    private val sessions = mutableMapOf<SessionId, DialogSession>()

    override fun save(session: DialogSession): DialogSession {
        sessions[session.id] = session
        return session
    }

    override fun findById(id: SessionId): DialogSession? = sessions[id]

    override fun findByTaskId(taskId: TaskId): DialogSession? =
        sessions.values.firstOrNull { it.taskId == taskId }

    override fun findActiveSession(): DialogSession? = sessions.values.firstOrNull()

    override fun delete(id: SessionId) {
        sessions.remove(id)
    }
}

private class RecordingRenderer : CliRenderer {
    val createdTasks = mutableListOf<TaskId>()
    val updatedTasks = mutableListOf<TaskId>()
    val deletedTasks = mutableListOf<TaskId>()
    val closedTasks = mutableListOf<TaskId>()
    val cancelledTasks = mutableListOf<TaskId>()
    var lastTaskList: List<Task> = emptyList()
    var lastTaskDetail: Task? = null
    val errors = mutableListOf<String>()
    val infos = mutableListOf<String>()

    override fun renderTaskHeader(metadata: TaskMetadata) = Unit
    override fun renderResult(result: TaskResult) = Unit
    override fun renderError(message: String) {
        errors.add(message)
    }

    override fun renderPrompt(state: CliState) = Unit
    override fun renderHelp(state: CliState) = Unit
    override fun renderParameters(state: CliState) = Unit
    override fun renderWelcome() = Unit
    override fun renderMenu(executors: List<io.averkhogliad.ai.challenge.week4.cli.application.executor.TaskExecutor>) =
        Unit
    override fun renderGoodbye() = Unit
    override fun renderRequestInfo(prompt: String, config: TaskExecutionConfig) = Unit
    override fun renderLoadingStart(message: String) = Unit
    override fun renderLoadingStop() = Unit
    override fun renderSuccess(message: String) = Unit
    override fun renderInfo(message: String) {
        infos.add(message)
    }

    override fun renderTaskList(tasks: List<Task>) {
        lastTaskList = tasks
    }

    override fun renderTaskDetail(task: Task) {
        lastTaskDetail = task
    }

    override fun renderTaskCreated(taskId: TaskId) {
        createdTasks.add(taskId)
    }

    override fun renderTaskUpdated(taskId: TaskId) {
        updatedTasks.add(taskId)
    }

    override fun renderTaskDeleted(taskId: TaskId) {
        deletedTasks.add(taskId)
    }

    override fun renderTaskClosed(taskId: TaskId) {
        closedTasks.add(taskId)
    }

    override fun renderTaskCancelled(taskId: TaskId) {
        cancelledTasks.add(taskId)
    }

    override fun renderStepCreated(step: TaskStep) = Unit
    override fun renderStepList(steps: List<TaskStep>) = Unit
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

package io.averkhogliad.ai.challenge.week3.cli.cli.handlers

import io.averkhogliad.ai.challenge.week3.cli.application.service.TodoTaskService
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
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("TodoTaskCommandHandler")
class TodoTaskCommandHandlerTest {

    private lateinit var repository: InMemoryTaskRepository
    private lateinit var todoTaskService: TodoTaskService
    private lateinit var memoryService: MemoryService
    private lateinit var renderer: RecordingRenderer
    private lateinit var descriptionInput: MutableList<String>
    private lateinit var handler: TodoTaskCommandHandler

    @BeforeEach
    fun setUp() {
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

    @Nested
    @DisplayName("task CRUD commands")
    inner class TaskCrudCommands {

        @Test
        @DisplayName("AddTask creates new task")
        fun `AddTask creates new task`() = runBlocking {
            descriptionInput.add("Task description")

            handler.handleAddTask(Command.AddTask("Test Task"), CliState())

            val tasks = todoTaskService.listTasks()
            assertEquals(1, tasks.size)
            assertEquals("Test Task", tasks[0].title)
            assertEquals("Task description", tasks[0].description)
            assertEquals(listOf(tasks[0].id), renderer.createdTasks)
        }

        @Test
        @DisplayName("ListTasks renders all tasks")
        fun `ListTasks renders all tasks`() = runBlocking {
            todoTaskService.addTask("Task 1")
            todoTaskService.addTask("Task 2")

            handler.handleListTasks(CliState())

            assertEquals(2, renderer.lastTaskList.size)
        }

        @Test
        @DisplayName("OpenTask sets currentTodoTaskId in state")
        fun `OpenTask sets currentTodoTaskId`() = runBlocking {
            val task = todoTaskService.addTask("Task to open")

            val newState = handler.handleOpenTask(Command.OpenTask(task.id), CliState())

            assertEquals(task.id.value, newState.currentTodoTaskId)
            assertEquals(false, newState.taskListMode)
            assertEquals(task.id, todoTaskService.currentTaskId)
            val memoryStatus = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, task.id)
            assertEquals(task.id, memoryStatus.taskId)
            assertEquals(task, renderer.lastTaskDetail)
        }

        @Test
        @DisplayName("edit without id uses currentTaskId")
        fun `edit without id uses currentTaskId`() = runBlocking {
            descriptionInput.add(EMPTY_INPUT)
            val task = todoTaskService.addTask("Original Title")
            handler.handleOpenTask(Command.OpenTask(task.id), CliState())

            handler.handleEditTask(Command.EditTask(null, "Updated Title"), CliState(currentTodoTaskId = task.id.value))

            val updated = todoTaskService.listTasks().find { it.id == task.id }
            assertNotNull(updated)
            assertEquals("Updated Title", updated.title)
            assertEquals(listOf(task.id), renderer.updatedTasks)
        }

        @Test
        @DisplayName("drop without id uses currentTaskId")
        fun `drop without id uses currentTaskId`() = runBlocking {
            val task = todoTaskService.addTask("Task to drop")
            handler.handleOpenTask(Command.OpenTask(task.id), CliState())

            handler.handleDropTask(Command.DropTask(null), CliState(currentTodoTaskId = task.id.value))

            assertTrue(todoTaskService.listTasks().isEmpty())
            assertNull(todoTaskService.currentTaskId)
            assertEquals(listOf(task.id), renderer.deletedTasks)
        }

        @Test
        @DisplayName("close without id uses currentTaskId")
        fun `close without id uses currentTaskId`() = runBlocking {
            val task = todoTaskService.addTask("Task to close")
            handler.handleOpenTask(Command.OpenTask(task.id), CliState())

            val newState = handler.handleCloseTask(Command.CloseTask(null), CliState(currentTodoTaskId = task.id.value))

            val closed = todoTaskService.listTasks().find { it.id == task.id }
            assertNotNull(closed)
            assertEquals(TaskStatus.CLOSED, closed.status)
            assertNull(todoTaskService.currentTaskId)
            assertNull(newState.currentTodoTaskId)
            assertEquals(true, newState.taskListMode)
            val memoryStatus = memoryService.getMemoryStatus(SessionLevel.TASK_LIST)
            assertEquals(SessionLevel.TASK_LIST, memoryStatus.level)
            assertEquals(listOf(task.id), renderer.closedTasks)
        }

        @Test
        @DisplayName("cancel without id uses currentTaskId")
        fun `cancel without id uses currentTaskId`() = runBlocking {
            val task = todoTaskService.addTask("Task to cancel")
            handler.handleOpenTask(Command.OpenTask(task.id), CliState())

            val newState =
                handler.handleCancelTask(Command.CancelTask(null), CliState(currentTodoTaskId = task.id.value))

            val cancelled = todoTaskService.listTasks().find { it.id == task.id }
            assertNotNull(cancelled)
            assertEquals(TaskStatus.CANCELLED, cancelled.status)
            assertNull(todoTaskService.currentTaskId)
            assertNull(newState.currentTodoTaskId)
            assertEquals(true, newState.taskListMode)
            val memoryStatus = memoryService.getMemoryStatus(SessionLevel.TASK_LIST)
            assertEquals(SessionLevel.TASK_LIST, memoryStatus.level)
            assertEquals(listOf(task.id), renderer.cancelledTasks)
        }

        @Test
        @DisplayName("close explicit different task preserves current state and memory context")
        fun `close explicit different task preserves current state and memory context`() = runBlocking {
            val currentTask = todoTaskService.addTask("Current task")
            val otherTask = todoTaskService.addTask("Other task")
            val initialState = handler.handleOpenTask(Command.OpenTask(currentTask.id), CliState())

            val newState = handler.handleCloseTask(Command.CloseTask(otherTask.id), initialState)

            val closed = todoTaskService.listTasks().find { it.id == otherTask.id }
            assertNotNull(closed)
            assertEquals(TaskStatus.CLOSED, closed.status)
            assertEquals(currentTask.id, todoTaskService.currentTaskId)
            assertEquals(initialState, newState)
            val memoryStatus = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, currentTask.id)
            assertEquals(SessionLevel.TASK_DETAIL, memoryStatus.level)
            assertEquals(currentTask.id, memoryStatus.taskId)
            assertEquals(listOf(otherTask.id), renderer.closedTasks)
        }

        @Test
        @DisplayName("cancel explicit different task preserves current state and memory context")
        fun `cancel explicit different task preserves current state and memory context`() = runBlocking {
            val currentTask = todoTaskService.addTask("Current task")
            val otherTask = todoTaskService.addTask("Other task")
            val initialState = handler.handleOpenTask(Command.OpenTask(currentTask.id), CliState())

            val newState = handler.handleCancelTask(Command.CancelTask(otherTask.id), initialState)

            val cancelled = todoTaskService.listTasks().find { it.id == otherTask.id }
            assertNotNull(cancelled)
            assertEquals(TaskStatus.CANCELLED, cancelled.status)
            assertEquals(currentTask.id, todoTaskService.currentTaskId)
            assertEquals(initialState, newState)
            val memoryStatus = memoryService.getMemoryStatus(SessionLevel.TASK_DETAIL, currentTask.id)
            assertEquals(SessionLevel.TASK_DETAIL, memoryStatus.level)
            assertEquals(currentTask.id, memoryStatus.taskId)
            assertEquals(listOf(otherTask.id), renderer.cancelledTasks)
        }

    }

    @Nested
    @DisplayName("Back command")
    inner class BackCommand {

        @Test
        @DisplayName("Back from task detail clears currentTodoTaskId and renders task list")
        fun `Back from task detail clears currentTodoTaskId`() = runBlocking {
            todoTaskService.addTask("Task")

            val newState = handler.handleBack(CliState(currentTodoTaskId = "task-id")) {}

            assertNull(newState.currentTodoTaskId)
            assertEquals(true, newState.taskListMode)
            val memoryStatus = memoryService.getMemoryStatus(SessionLevel.TASK_LIST)
            assertEquals(SessionLevel.TASK_LIST, memoryStatus.level)
            assertEquals(1, renderer.lastTaskList.size)
        }

        @Test
        @DisplayName("Back from task list renders main menu")
        fun `Back from task list renders main menu`() = runBlocking {
            var mainMenuRendered = false

            val newState = handler.handleBack(CliState(taskListMode = true)) {
                mainMenuRendered = true
            }

            assertNull(newState.currentTaskId)
            assertEquals(false, newState.taskListMode)
            val memoryStatus = memoryService.getMemoryStatus(SessionLevel.TASK_LIST)
            assertEquals(SessionLevel.TASK_LIST, memoryStatus.level)
            assertTrue(mainMenuRendered)
        }
    }

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

    override fun renderMenu(executors: List<io.averkhogliad.ai.challenge.week3.cli.application.executor.TaskExecutor>) =
        Unit

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

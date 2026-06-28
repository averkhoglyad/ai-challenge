package io.averkhogliad.ai.challenge.week3.cli.cli.handlers

import io.averkhogliad.ai.challenge.week3.cli.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week3.cli.cli.CliRenderer
import io.averkhogliad.ai.challenge.week3.cli.cli.CliState
import io.averkhogliad.ai.challenge.week3.cli.cli.commands.Command
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.service.MemoryService

class TodoTaskCommandHandler(
    private val todoTaskService: TodoTaskService,
    private val memoryService: MemoryService,
    private val renderer: CliRenderer,
    private val readMultiline: () -> String,
) {

    suspend fun handleBack(state: CliState, renderMainMenu: () -> Unit): CliState {
        val newState = if (state.currentTodoTaskId != null) {
            memoryService.switchToTaskListLevel()
            state.copy(currentTodoTaskId = null, taskListMode = true)
        } else if (state.taskListMode) {
            memoryService.switchToTaskListLevel()
            state.copy(currentTaskId = null, taskListMode = false)
        } else {
            state.copy(currentTaskId = null, currentTodoTaskId = null, taskListMode = false)
        }

        if (newState.taskListMode) {
            renderTaskList()
        } else {
            renderMainMenu()
        }
        return newState
    }

    suspend fun handleAddTask(command: Command.AddTask, state: CliState): CliState =
        handleTodoError(state) {
            renderer.renderInfo(ADD_DESCRIPTION_PROMPT)
            val description = readMultiline()
            val task = todoTaskService.addTask(
                command.title,
                description = description.takeIf { it.isNotBlank() }
            )
            renderer.renderTaskCreated(task.id)
            state
        }

    suspend fun handleListTasks(state: CliState): CliState =
        handleTodoError(state) {
            renderTaskList()
            state
        }

    suspend fun handleEditTask(command: Command.EditTask, state: CliState): CliState =
        handleTodoError(state) {
            val task = todoTaskService.editTask(command.id, command.title)
            renderer.renderInfo(EDIT_DESCRIPTION_PROMPT)
            val newDescription = readMultiline()
            if (newDescription.isNotBlank()) {
                todoTaskService.updateDescription(command.id, newDescription)
            }
            renderer.renderTaskUpdated(task.id)
            state
        }

    suspend fun handleDropTask(command: Command.DropTask, state: CliState): CliState =
        handleTodoError(state) {
            val taskId = command.id ?: todoTaskService.currentTaskId
            todoTaskService.dropTask(command.id)
            if (taskId != null) {
                renderer.renderTaskDeleted(taskId)
            }
            state
        }

    suspend fun handleOpenTask(command: Command.OpenTask, state: CliState): CliState =
        handleTodoError(state) {
            val task = todoTaskService.openTask(command.id)
            memoryService.switchToTaskLevel(TaskId(command.id.value))
            renderer.renderTaskDetail(task)
            state.copy(currentTodoTaskId = command.id.value, taskListMode = false)
        }

    suspend fun handleCloseTask(command: Command.CloseTask, state: CliState): CliState =
        handleTodoError(state) {
            val shouldLeaveCurrentTask = shouldLeaveCurrentTask(command.id, state)
            val task = todoTaskService.closeTask(command.id)
            renderer.renderTaskClosed(task.id)
            if (shouldLeaveCurrentTask) {
                memoryService.switchToTaskListLevel()
                state.copy(currentTodoTaskId = null, taskListMode = true)
            } else {
                state
            }
        }

    suspend fun handleCancelTask(command: Command.CancelTask, state: CliState): CliState =
        handleTodoError(state) {
            val shouldLeaveCurrentTask = shouldLeaveCurrentTask(command.id, state)
            val task = todoTaskService.cancelTask(command.id)
            renderer.renderTaskCancelled(task.id)
            if (shouldLeaveCurrentTask) {
                memoryService.switchToTaskListLevel()
                state.copy(currentTodoTaskId = null, taskListMode = true)
            } else {
                state
            }
        }

    private fun shouldLeaveCurrentTask(commandTaskId: TaskId?, state: CliState): Boolean =
        commandTaskId == null || commandTaskId.value == state.currentTodoTaskId


    private suspend fun renderTaskList() {
        val tasks = todoTaskService.listTasks()
        renderer.renderTaskList(tasks)
    }

    private inline fun handleTodoError(state: CliState, action: () -> CliState): CliState =
        try {
            action()
        } catch (e: Exception) {
            renderer.renderError(e.message ?: UNKNOWN_ERROR_MESSAGE)
            state
        }

    companion object {
        private const val ADD_DESCRIPTION_PROMPT =
            "Введите описание задачи (Enter — пропустить, пустая строка — завершить):"
        private const val EDIT_DESCRIPTION_PROMPT =
            "Введите новое описание (Enter — оставить прежним, пустая строка — завершить):"
        private const val UNKNOWN_ERROR_MESSAGE = "Unknown error"
    }
}

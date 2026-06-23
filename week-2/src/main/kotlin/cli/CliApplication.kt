package io.averkhogliad.ai.challenge.week2.cli

import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.application.InvariantService
import io.averkhogliad.ai.challenge.week2.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week2.application.handler.DebugCommandHandler
import io.averkhogliad.ai.challenge.week2.application.handler.PlanCommandHandler
import io.averkhogliad.ai.challenge.week2.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week2.cli.commands.Command
import io.averkhogliad.ai.challenge.week2.cli.commands.CommandContext
import io.averkhogliad.ai.challenge.week2.cli.commands.CommandParser
import io.averkhogliad.ai.challenge.week2.cli.handlers.FsmCommandHandler
import io.averkhogliad.ai.challenge.week2.cli.handlers.InvariantCommandHandler
import io.averkhogliad.ai.challenge.week2.cli.handlers.ProfileCommandHandler
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.model.FactId
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.service.*
import kotlinx.coroutines.runBlocking

class CliApplication(
    private val executors: Map<TaskId, TaskExecutor>,
    private val renderer: CliRenderer = ConsoleCliRenderer(),
    private val todoTaskService: TodoTaskService,
    private val memoryService: MemoryService,
    private val taskStepRepository: TaskStepRepository,
    private val factRepository: FactRepository,
    private val dialogService: DialogService,
    private val profileRepository: ProfileRepository,
    private val planCommandHandler: PlanCommandHandler,
    private val commandEngine: CommandEngine,
    private val debugCommandHandler: DebugCommandHandler,
    private val invariantService: InvariantService,
    private val invariantRepository: InvariantRepository,
) : AutoCloseable {

    private val handler = CommandHandler(
        executors = executors,
        todoTaskService = todoTaskService,
        memoryService = memoryService,
        taskStepRepository = taskStepRepository,
        factRepository = factRepository
    )

    private val fsmHandler = FsmCommandHandler(
        commandEngine = commandEngine,
        debugCommandHandler = debugCommandHandler,
        memoryService = memoryService,
        profileRepository = profileRepository,
        invariantService = invariantService,
        renderer = renderer
    )

    private val invariantHandler = InvariantCommandHandler(
        invariantService = invariantService,
        renderer = renderer,
        readInput = { readlnOrNull() }
    )

    private val profileHandler = ProfileCommandHandler(
        handler = handler,
        renderer = renderer,
        readMultiline = { readMultilineInput() }
    )

    fun run(args: Array<String>) {
        runBlocking {
            repl()
        }
    }

    override fun close() {
        try {
            invariantRepository.close()
        } catch (e: Exception) {
            System.err.println("Warning: Failed to close InvariantRepository: ${e.message}")
        }
    }

    private suspend fun repl() {
        var state = CliState()

        renderer.renderWelcome()
        renderer.renderMenu(handler.getAllExecutors())

        while (state.isRunning) {
            try {
                renderer.renderPrompt(state)
                val input = readlnOrNull() ?: break
                val context = buildCommandContext(state)
                val command = CommandParser.parse(input, context)
                state = handleCommandWithRendering(command, state)
            } catch (e: Exception) {
                renderer.renderError("Неожиданная ошибка: ${e.message}")
            }
        }

        renderer.renderGoodbye()
    }

    private suspend fun handleCommandWithRendering(command: Command, state: CliState): CliState {
        return when (command) {
            is Command.Help -> {
                renderer.renderHelp(state)
                state
            }

            is Command.ShowParameters -> {
                renderer.renderParameters(state)
                state
            }

            is Command.Unknown -> {
                renderer.renderError("Неизвестная команда: ${command.raw}")
                state
            }

            is Command.SelectTask -> {
                val newState = handler.handle(command, state)
                val executor = handler.getExecutor(TaskId(command.taskId.toString()))
                if (executor != null) {
                    renderer.renderTaskHeader(executor.metadata)
                } else {
                    renderer.renderError("Задача ${command.taskId} не найдена")
                }
                newState
            }

            is Command.Back -> {
                val newState = handler.handle(command, state)
                if (newState.taskListMode) {
                    val tasks = todoTaskService.listTasks()
                    renderer.renderTaskList(tasks)
                } else {
                    renderer.renderMenu(handler.getAllExecutors())
                }
                newState
            }

            is Command.Quit -> {
                handler.handle(command, state)
            }

            is Command.Debug -> {
                val newState = handler.handle(command, state)
                state
            }

            is Command.UserInput -> {
                if (commandEngine.hasActiveCommand()) {
                    val activeState = commandEngine.getActiveState()
                    if (activeState?.commandName == "plan") {
                        val result = planCommandHandler.handleUserInput(command.text)
                        renderer.renderInfo(result)
                        return state
                    }
                }

                if (state.currentTaskId != null) {
                    renderer.renderRequestInfo(command.text, state.executionConfig)
                    renderer.renderLoadingStart("Отправка запроса...")
                    val (newState, result) = handler.executeUserInput(command, state)
                    renderer.renderLoadingStop()
                    when (result) {
                        is TaskResult.Success -> renderer.renderResult(result)
                        is TaskResult.Error -> renderer.renderError(result.message)
                        is TaskResult.Partial -> renderer.renderResult(result)
                        null -> renderer.renderMenu(handler.getAllExecutors())
                    }
                    newState
                } else {
                    renderer.renderLoadingStart("Общение с ассистентом...")
                    val level = if (state.currentTodoTaskId != null) {
                        io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel.TASK_DETAIL
                    } else {
                        io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel.TASK_LIST
                    }
                    val taskId = state.currentTodoTaskId?.let { TaskId(it) }
                    val result = dialogService.chat(command.text, level, taskId)
                    renderer.renderLoadingStop()
                    when (result) {
                        is TaskResult.Success -> renderer.renderResult(result)
                        is TaskResult.Error -> renderer.renderError(result.message)
                        is TaskResult.Partial -> renderer.renderResult(result)
                    }
                    state
                }
            }

            is Command.SetTemperature,
            is Command.SetMaxTokens,
            is Command.SetStopSequences,
            is Command.ResetParameters,
            is Command.ShowState,
            is Command.Abort -> {
                handler.handle(command, state)
            }

            // Goto commands delegated to FsmCommandHandler
            is Command.Goto -> {
                fsmHandler.handleGoto(state); state
            }

            is Command.GotoState -> {
                fsmHandler.handleGotoState(command, state); state
            }

            // Invariant management commands delegated to InvariantCommandHandler
            is Command.InvariantAdd -> {
                invariantHandler.handleInvariantAdd(command, state); state
            }

            is Command.InvariantList -> {
                invariantHandler.handleInvariantList(state); state
            }

            is Command.InvariantRemove -> {
                invariantHandler.handleInvariantRemove(command, state); state
            }

            // Dialog commands (no-op — dialog functionality removed)
            is Command.NewDialog -> {
                renderer.renderInfo("Команды диалогов больше не поддерживаются")
                state
            }

            is Command.ListDialogs -> {
                renderer.renderInfo("Команды диалогов больше не поддерживаются")
                state
            }

            is Command.DeleteDialog -> {
                renderer.renderInfo("Команды диалогов больше не поддерживаются")
                state
            }

            is Command.SwitchDialog -> {
                renderer.renderInfo("Команды диалогов больше не поддерживаются")
                state
            }

            is Command.ShowHistory -> {
                renderer.renderInfo("Команды диалогов больше не поддерживаются")
                state
            }

            // LLM integration commands
            is Command.Plan -> {
                val taskId = state.currentTodoTaskId?.toIntOrNull()
                val result = planCommandHandler.execute(taskId)
                renderer.renderInfo(result)
                state
            }

            is Command.PlanSteps -> {
                renderer.renderLoadingStart("Запрос плана шагов...")
                val level = if (state.currentTodoTaskId != null) {
                    io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel.TASK_DETAIL
                } else {
                    io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel.TASK_LIST
                }
                val taskId = state.currentTodoTaskId?.let { TaskId(it) }
                val result = dialogService.planSteps(command.title, command.description, level, taskId)
                renderer.renderLoadingStop()
                when (result) {
                    is TaskResult.Success -> renderer.renderResult(result)
                    is TaskResult.Error -> renderer.renderError(result.message)
                    is TaskResult.Partial -> renderer.renderResult(result)
                }
                state
            }

            // Compression commands (no-op for now)
            is Command.SetCompressionEnabled,
            is Command.SetCompressionWindow,
            is Command.SetCompressionBlock,
            is Command.ShowCompressionStatus -> {
                renderer.renderInfo("Команды сжатия контекста будут доступны в Task 4")
                state
            }

            // Strategy commands (no-op for now)
            is Command.ShowStrategyMenu,
            is Command.SwitchStrategy,
            is Command.ShowCurrentStrategy,
            is Command.CreateBranch,
            is Command.SwitchBranch,
            is Command.ListBranches,
            is Command.CreateCheckpoint,
            is Command.ListCheckpoints,
            is Command.ListFacts,
            is Command.ClearFacts,
            is Command.AddFact,
            is Command.RemoveFact -> {
                renderer.renderInfo("Команды стратегий будут доступны в Task 5")
                state
            }

            // Task management commands (todo-manager)
            is Command.AddTask -> {
                try {
                    renderer.renderInfo("Введите описание задачи (Enter — пропустить, пустая строка — завершить):")
                    val description = readMultilineInput()
                    val task = todoTaskService.addTask(
                        command.title,
                        description = description.takeIf { it.isNotBlank() }
                    )
                    renderer.renderTaskCreated(task.id)
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                }
                state
            }

            is Command.ListTasks -> {
                try {
                    val tasks = todoTaskService.listTasks()
                    renderer.renderTaskList(tasks)
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                }
                state
            }

            is Command.EditTask -> {
                try {
                    val task = todoTaskService.editTask(command.id, command.title)
                    if (task == null) {
                        renderer.renderError("TodoTaskService not available")
                        state
                    } else {
                        renderer.renderInfo("Введите новое описание (Enter — оставить прежним, пустая строка — завершить):")
                        val newDescription = readMultilineInput()
                        if (newDescription.isNotBlank()) {
                            todoTaskService.updateDescription(command.id, newDescription)
                        }
                        renderer.renderTaskUpdated(task.id)
                        state
                    }
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                    state
                }
            }

            is Command.DropTask -> {
                try {
                    val taskId = command.id ?: todoTaskService.currentTaskId
                    todoTaskService.dropTask(command.id)
                    if (taskId != null) {
                        renderer.renderTaskDeleted(taskId)
                    }
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                }
                state
            }

            is Command.OpenTask -> {
                try {
                    val task = todoTaskService.openTask(command.id)
                    renderer.renderTaskDetail(task)
                    state.copy(currentTodoTaskId = command.id.value)
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                    state
                }
            }

            is Command.CloseTask -> {
                try {
                    val task = todoTaskService.closeTask(command.id)
                    renderer.renderTaskClosed(task.id)
                    state.copy(currentTodoTaskId = null)
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                    state
                }
            }

            is Command.CancelTask -> {
                try {
                    val task = todoTaskService.cancelTask(command.id)
                    renderer.renderTaskCancelled(task.id)
                    state.copy(currentTodoTaskId = null)
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                    state
                }
            }

            // Step management commands
            is Command.AddStep -> {
                try {
                    val newState = handler.handle(command, state)
                    renderer.renderInfo("Step added")
                    newState
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                    state
                }
            }

            is Command.ListSteps -> {
                try {
                    handler.handle(command, state)
                    state
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                    state
                }
            }

            is Command.CompleteStep -> {
                try {
                    val newState = handler.handle(command, state)
                    renderer.renderInfo("Step completed")
                    newState
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                    state
                }
            }

            // Memory management commands
            is Command.ClearMemory -> {
                try {
                    val newState = handler.handle(command, state)
                    renderer.renderMemoryCleared()
                    newState
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                    state
                }
            }

            is Command.ShowStatus -> {
                try {
                    val level = if (state.currentTodoTaskId != null) {
                        io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel.TASK_DETAIL
                    } else {
                        io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel.TASK_LIST
                    }
                    val taskId = state.currentTodoTaskId?.let { TaskId(it) }
                    val status = memoryService.getMemoryStatus(level, taskId)
                    renderer.renderMemoryStatus(status)

                    val activeProfile = profileRepository.findActive()
                    renderer.renderStatusProfile(activeProfile?.name)

                    val isDebugEnabled = debugCommandHandler.isEnabled() ?: false
                    renderer.renderStatusDebug(isDebugEnabled)

                    val activeState = commandEngine.getActiveState()
                    val availableTransitions = if (activeState != null) {
                        commandEngine.getAvailableTransitions()
                    } else {
                        emptyList()
                    }
                    renderer.renderStatusFsm(activeState?.currentStage, availableTransitions)
                    val invariantCount = invariantService.count()
                    renderer.renderStatusInvariants(invariantCount)
                    state
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                    state
                }
            }

            // LTM (Long-Term Memory) commands
            is Command.SaveFact -> {
                try {
                    val newState = handler.handle(command, state)
                    val facts = factRepository.findAll()
                    val savedFact = facts.maxByOrNull { it.createdAt }
                    if (savedFact != null) {
                        renderer.renderFactSaved(savedFact)
                    }
                    newState
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                    state
                }
            }

            is Command.ListLtmFacts -> {
                try {
                    handler.handle(command, state)
                    val facts = factRepository.findAll()
                    renderer.renderFactList(facts)
                    state
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                    state
                }
            }

            is Command.ForgetFact -> {
                try {
                    val factId = command.factId
                    val exists = factRepository.findById(FactId(factId)) != null
                    if (exists) {
                        factRepository.delete(FactId(factId))
                        renderer.renderFactForgotten(factId)
                    } else {
                        renderer.renderFactNotFound(factId)
                    }
                    state
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                    state
                }
            }

            is Command.SearchFacts -> {
                try {
                    handler.handle(command, state)
                    val facts = factRepository.search(command.query)
                    if (facts.isEmpty()) {
                        renderer.renderFactSearchEmpty(command.query)
                    } else {
                        renderer.renderFactSearchResults(facts, command.query)
                    }
                    state
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                    state
                }
            }

            // Profile management commands delegated to ProfileCommandHandler
            is Command.ProfileList -> {
                profileHandler.handleProfileList(state); state
            }

            is Command.ProfileNew -> {
                profileHandler.handleProfileNew(command, state); state
            }

            is Command.ProfileUse -> {
                profileHandler.handleProfileUse(command, state); state
            }

            is Command.ProfileEdit -> {
                profileHandler.handleProfileEdit(command, state); state
            }

            is Command.ProfileDelete -> {
                profileHandler.handleProfileDelete(command, state); state
            }

            is Command.ProfileShow -> {
                profileHandler.handleProfileShow(command, state); state
            }
        }
    }

    private fun readMultilineInput(): String {
        val lines = mutableListOf<String>()
        while (true) {
            val line = readlnOrNull() ?: break
            if (line.isEmpty()) break
            lines.add(line)
        }
        return lines.joinToString("\n")
    }

    private fun buildCommandContext(state: CliState): CommandContext {
        val isTaskActive = state.currentTaskId != null || state.currentTodoTaskId != null || state.taskListMode
        if (!isTaskActive) {
            return CommandContext.TASK_SELECTION
        }

        val taskId = state.currentTaskId ?: 1

        val availableCommands = mutableSetOf(
            "help", "h", "quit", "q", "back", "b",
            "add", "list", "tasks", "edit", "drop", "open", "close", "cancel",
            "step-add", "step-list", "step-done",
            "temp", "maxtokens", "reset", "params", "stop",
            "plan",
            "debug", "state", "abort",
            "status", "clear", "ctx-save", "ctx-list", "ctx-forget", "ctx-search",
            "profile-new", "profile-list", "profile-use", "profile-edit", "profile-delete", "profile-show"
        )

        return CommandContext(
            currentTaskId = taskId,
            availableCommands = availableCommands
        )
    }
}

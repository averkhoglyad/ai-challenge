package io.averkhogliad.ai.challenge.week2.cli

import io.averkhogliad.ai.challenge.week2.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week2.application.executor.TaskManagerExecutor
import io.averkhogliad.ai.challenge.week2.cli.commands.Command
import io.averkhogliad.ai.challenge.week2.cli.commands.CommandContext
import io.averkhogliad.ai.challenge.week2.cli.commands.CommandParser
import io.averkhogliad.ai.challenge.week2.domain.TaskId
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.model.FactId
import io.averkhogliad.ai.challenge.week2.domain.service.FactRepository
import io.averkhogliad.ai.challenge.week2.domain.service.ResourceManager
import io.averkhogliad.ai.challenge.week2.domain.service.TaskStepRepository
import kotlinx.coroutines.runBlocking
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId as ModelTaskId

/**
 * CLI-приложение на основе Clean Architecture.
 *
 * ## Архитектура
 * Это Imperative Shell — тонкая оболочка, которая:
 * 1. Читает ввод пользователя (stdin)
 * 2. Парсит ввод в typed commands ([CommandParser])
 * 3. Обрабатывает команды ([CommandHandler])
 * 4. Рендерит результат ([CliRenderer])
 *
 * Не содержит бизнес-логики — она делегирована в [TaskExecutor].
 * Не содержит логики рендеринга — она делегирована в [CliRenderer].
 *
 * ## REPL-цикл
 * ```
 * while (state.isRunning) {
 *     prompt → parse → handle → render → repeat
 * }
 * ```
 *
 * ## Управление ресурсами
 * [CliApplication] владеет [resourceManager] и вызывает его при завершении работы ([close]).
 * Это гарантирует освобождение HTTP-соединений и пулов потоков.
 *
 * @param executors мапа TaskId → TaskExecutor
 * @param renderer рендерер CLI вывода
 * @param resourceManager порт для управления ресурсами (закрывается при завершении работы)
 */
class CliApplication(
    private val executors: Map<TaskId, TaskExecutor>,
    private val renderer: CliRenderer = ConsoleCliRenderer(),
    private val resourceManager: ResourceManager? = null,
    private val taskManagerExecutor: TaskManagerExecutor? = null,
    private val memoryService: io.averkhogliad.ai.challenge.week2.domain.service.MemoryService? = null,
    private val taskStepRepository: TaskStepRepository? = null,
    private val factRepository: FactRepository? = null,
    private val dialogService: io.averkhogliad.ai.challenge.week2.application.DialogService? = null
) : AutoCloseable {

    private val handler = CommandHandler(
        executors = executors,
        taskManagerExecutor = taskManagerExecutor,
        memoryService = memoryService,
        taskStepRepository = taskStepRepository,
        factRepository = factRepository,
        dialogService = dialogService
    )

    fun run(args: Array<String>) {
        runBlocking {
            repl()
        }
    }

    override fun close() {
        try {
            resourceManager?.close()
        } catch (e: Exception) {
            System.err.println("Warning: Failed to close ResourceManager: ${e.message}")
        }
    }

    /**
     * Основной REPL-цикл.
     */
    private suspend fun repl() {
        var state = CliState()

        renderer.renderWelcome()
        renderer.renderMenu(handler.getAllExecutors())

        while (state.isRunning) {
            try {
                // 1. Показать промпт
                renderer.renderPrompt(state)

                // 2. Прочитать ввод
                val input = readlnOrNull() ?: break

                // 3. Построить контекст парсинга
                val context = buildCommandContext(state)

                // 4. Парсинг
                val command = CommandParser.parse(input, context)

                // 5. Обработка команды + рендеринг
                state = handleCommandWithRendering(command, state)

            } catch (e: Exception) {
                renderer.renderError("Неожиданная ошибка: ${e.message}")
            }
        }

        renderer.renderGoodbye()
    }

    /**
     * Обрабатывает команду и выполняет соответствующий рендеринг.
     */
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
                val executor = handler.getExecutor(TaskId(command.taskId))
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
                    // Возврат в список задач (не в главное меню)
                    val tasks = taskManagerExecutor?.handleListTasks() ?: emptyList()
                    renderer.renderTaskList(tasks)
                } else {
                    renderer.renderMenu(handler.getAllExecutors())
                }
                newState
            }

            is Command.Quit -> {
                handler.handle(command, state)
            }

            is Command.UserInput -> {
                if (dialogService == null && state.currentTaskId != null) {
                    // Fallback: use old task executors if dialogService not available but task selected
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
                } else if (dialogService != null) {
                    renderer.renderLoadingStart("Общение с ассистентом...")
                    val level = if (state.currentTodoTaskId != null) {
                        io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel.TASK_DETAIL
                    } else {
                        io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel.TASK_LIST
                    }
                    val taskId = state.currentTodoTaskId?.let { ModelTaskId(it) }
                    val result = dialogService.chat(command.text, level, taskId)
                    renderer.renderLoadingStop()
                    when (result) {
                        is TaskResult.Success -> renderer.renderResult(result)
                        is TaskResult.Error -> renderer.renderError(result.message)
                        is TaskResult.Partial -> renderer.renderResult(result)
                    }
                    state
                } else {
                    renderer.renderError("Ни один executor не доступен. Выберите задачу или настройте LLM.")
                    state
                }
            }

            is Command.SetTemperature,
            is Command.SetMaxTokens,
            is Command.SetStopSequences,
            is Command.ResetParameters -> {
                handler.handle(command, state)
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
            is Command.PlanSteps -> {
                if (dialogService == null) {
                    renderer.renderError("DialogService not available — LLM integration not configured")
                    state
                } else {
                    renderer.renderLoadingStart("Запрос плана шагов...")
                    val level = if (state.currentTodoTaskId != null) {
                        io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel.TASK_DETAIL
                    } else {
                        io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel.TASK_LIST
                    }
                    val taskId = state.currentTodoTaskId?.let { ModelTaskId(it) }
                    val result = dialogService.planSteps(command.title, command.description, level, taskId)
                    renderer.renderLoadingStop()
                    when (result) {
                        is TaskResult.Success -> renderer.renderResult(result)
                        is TaskResult.Error -> renderer.renderError(result.message)
                        is TaskResult.Partial -> renderer.renderResult(result)
                    }
                    state
                }
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
                    val task = taskManagerExecutor?.handleAddTask(command.title)
                    if (task != null) {
                        renderer.renderTaskCreated(task.id)
                    } else {
                        renderer.renderError("TaskManagerExecutor not available")
                    }
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                }
                state
            }

            is Command.ListTasks -> {
                try {
                    val tasks = taskManagerExecutor?.handleListTasks() ?: emptyList()
                    renderer.renderTaskList(tasks)
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                }
                state
            }

            is Command.EditTask -> {
                try {
                    val task = taskManagerExecutor?.handleEditTask(command.id, command.title)
                    if (task != null) {
                        renderer.renderTaskUpdated(task.id)
                    } else {
                        renderer.renderError("TaskManagerExecutor not available")
                    }
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                }
                state
            }

            is Command.DropTask -> {
                try {
                    val taskId = command.id ?: taskManagerExecutor?.currentTaskId
                    taskManagerExecutor?.handleDropTask(command.id)
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
                    val task = taskManagerExecutor?.handleOpenTask(command.id)
                    if (task != null) {
                        renderer.renderTaskDetail(task)
                        state.copy(currentTodoTaskId = command.id.value)
                    } else {
                        renderer.renderError("TaskManagerExecutor not available")
                        state
                    }
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                    state
                }
            }

            is Command.CloseTask -> {
                try {
                    val task = taskManagerExecutor?.handleCloseTask(command.id)
                    if (task != null) {
                        renderer.renderTaskClosed(task.id)
                        state.copy(currentTodoTaskId = null)
                    } else {
                        renderer.renderError("TaskManagerExecutor not available")
                        state
                    }
                } catch (e: Exception) {
                    renderer.renderError(e.message ?: "Unknown error")
                    state
                }
            }

            is Command.CancelTask -> {
                try {
                    val task = taskManagerExecutor?.handleCancelTask(command.id)
                    if (task != null) {
                        renderer.renderTaskCancelled(task.id)
                        state.copy(currentTodoTaskId = null)
                    } else {
                        renderer.renderError("TaskManagerExecutor not available")
                        state
                    }
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
                    val taskId =
                        state.currentTodoTaskId?.let { io.averkhogliad.ai.challenge.week2.domain.model.TaskId(it) }
                    val status = memoryService?.getMemoryStatus(level, taskId)
                    if (status != null) {
                        renderer.renderMemoryStatus(status)
                    } else {
                        renderer.renderInfo("Memory service not available")
                    }
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
                    // Получаем последний сохранённый факт для рендеринга
                    val facts = factRepository?.findAll() ?: emptyList()
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
                    val facts = factRepository?.findAll() ?: emptyList()
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
                    val exists = factRepository?.findById(FactId(factId)) != null
                    if (exists) {
                        factRepository?.delete(FactId(factId))
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
                    val facts = factRepository?.search(command.query) ?: emptyList()
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
        }
    }

    private fun buildCommandContext(state: CliState): CommandContext {
        // Активен ли какой-либо контекст задачи:
        // - старый executor (currentTaskId: Int)
        // - новый todo-менеджер (currentTodoTaskId: String / UUID)
        // - режим списка задач без конкретной открытой задачи (taskListMode)
        val isTaskActive = state.currentTaskId != null || state.currentTodoTaskId != null || state.taskListMode
        if (!isTaskActive) {
            return CommandContext.TASK_SELECTION
        }

        // currentTaskId для CommandContext (Int?).
        // Если активна задача todo-менеджера (UUID-строка), используем 1 как placeholder.
        val taskId = state.currentTaskId ?: 1

        val availableCommands = mutableSetOf(
            // Global
            "help", "h", "quit", "q", "back", "b",
            // Task management
            "add", "list", "edit", "drop", "open", "close", "cancel",
            // Step management
            "step-add", "step-list", "step-done",
            // LLM parameters
            "temp", "maxtokens", "reset", "params", "stop",
            // LLM integration
            "plan",
            // Memory management
            "status", "clear", "ctx-save", "ctx-list", "ctx-forget"
        )

        return CommandContext(
            currentTaskId = taskId,
            availableCommands = availableCommands
        )
    }
}

package io.averkhogliad.ai.challenge.week2.cli

import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.application.executor.*
import io.averkhogliad.ai.challenge.week2.cli.commands.Command
import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskId
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.*
import io.averkhogliad.ai.challenge.week2.domain.service.FactRepository
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week2.domain.service.TaskStepRepository
import java.time.Instant
import java.util.*
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId as ModelTaskId

class CommandHandler(
    private val executors: Map<TaskId, TaskExecutor>,
    private val taskManagerExecutor: TaskManagerExecutor? = null,
    private val memoryService: MemoryService? = null,
    private val taskStepRepository: TaskStepRepository? = null,
    private val factRepository: FactRepository? = null,
    private val dialogService: DialogService? = null,
    private val planCommandExecutor: PlanCommandExecutor? = null,
    private val debugCommandExecutor: DebugCommandExecutor? = null
) {

    suspend fun handle(command: Command, state: CliState): CliState {
        return when (command) {
            is Command.Help -> state
            is Command.Back -> {
                if (state.currentTodoTaskId != null) {
                    // Уровень 1: выход из выбранной задачи в список задач
                    memoryService?.switchToTaskListLevel()
                    state.copy(currentTodoTaskId = null, taskListMode = true)
                } else if (state.taskListMode) {
                    // Уровень 2: выход из списка задач в главное меню
                    memoryService?.switchToTaskListLevel()
                    state.copy(currentTaskId = null, taskListMode = false)
                } else {
                    // Нет контекста задачи — просто сбрасываем в главное меню
                    state.copy(currentTaskId = null, currentTodoTaskId = null, taskListMode = false)
                }
            }

            is Command.Quit -> state.copy(isRunning = false)
            is Command.SelectTask -> state.copy(currentTaskId = command.taskId)

            is Command.SetTemperature -> state.copy(
                executionConfig = state.executionConfig.copy(temperature = command.value)
            )

            is Command.SetMaxTokens -> state.copy(
                executionConfig = state.executionConfig.copy(maxTokens = command.value)
            )

            is Command.SetStopSequences -> state.copy(
                executionConfig = state.executionConfig.copy(stopSequences = command.values)
            )

            is Command.ResetParameters -> state.copy(
                executionConfig = TaskExecutionConfig()
            )

            is Command.ShowParameters -> state

            // Dialog commands (no-op — dialog functionality removed)
            is Command.NewDialog -> state
            is Command.ListDialogs -> state
            is Command.DeleteDialog -> state
            is Command.SwitchDialog -> state
            is Command.ShowHistory -> state

            // Goto commands (no-op for CommandHandler, handled by CliApplication)
            is Command.Goto -> state
            is Command.GotoState -> state

            // Compression commands (no-op for now, handled by CliApplication)
            is Command.SetCompressionEnabled -> state
            is Command.SetCompressionWindow -> state
            is Command.SetCompressionBlock -> state
            is Command.ShowCompressionStatus -> state

            // Strategy commands (no-op for now, handled by CliApplication)
            is Command.ShowStrategyMenu -> state
            is Command.SwitchStrategy -> state
            is Command.ShowCurrentStrategy -> state
            is Command.CreateBranch -> state
            is Command.SwitchBranch -> state
            is Command.ListBranches -> state
            is Command.CreateCheckpoint -> state
            is Command.ListCheckpoints -> state
            is Command.ListFacts -> state
            is Command.ClearFacts -> state
            is Command.AddFact -> state
            is Command.RemoveFact -> state

            // Task management commands (todo-manager)
            is Command.AddTask -> {
                taskManagerExecutor?.handleAddTask(command.title)
                state
            }

            is Command.ListTasks -> {
                taskManagerExecutor?.handleListTasks()
                state
            }

            is Command.EditTask -> {
                taskManagerExecutor?.handleEditTask(command.id, command.title)
                state
            }

            is Command.DropTask -> {
                taskManagerExecutor?.handleDropTask(command.id)
                state
            }

            is Command.OpenTask -> {
                taskManagerExecutor?.handleOpenTask(command.id)
                // Переключение STM на уровень задачи
                memoryService?.switchToTaskLevel(ModelTaskId(command.id.value))
                state.copy(currentTodoTaskId = command.id.value, taskListMode = false)
            }

            is Command.CloseTask -> {
                taskManagerExecutor?.handleCloseTask(command.id)
                // Очищаем currentTodoTaskId только если закрывается текущая задача
                val isCurrentTask = command.id == null || command.id?.value == state.currentTodoTaskId
                if (isCurrentTask) state.copy(currentTodoTaskId = null) else state
            }

            is Command.CancelTask -> {
                taskManagerExecutor?.handleCancelTask(command.id)
                state.copy(currentTodoTaskId = null)
            }

            // Step management commands
            is Command.AddStep -> {
                requireTaskOpen(state)
                val taskId = ModelTaskId(state.currentTodoTaskId!!)
                val step = TaskStep(
                    id = TaskStepId(UUID.randomUUID().toString()),
                    taskId = taskId,
                    text = command.text,
                    isCompleted = false,
                    order = (taskStepRepository?.countByTaskId(taskId) ?: 0),
                    createdAt = Instant.now()
                )
                taskStepRepository?.save(step)
                updateWorkingMemorySteps(taskId)
                state
            }

            is Command.ListSteps -> {
                requireTaskOpen(state)
                val taskId = ModelTaskId(state.currentTodoTaskId!!)
                val steps = taskStepRepository?.findByTaskId(taskId) ?: emptyList()
                // Rendering is handled by CliApplication
                state
            }

            is Command.CompleteStep -> {
                requireTaskOpen(state)
                val stepId = TaskStepId(command.stepId)
                val step = taskStepRepository?.findById(stepId)
                    ?: throw IllegalStateException("Step not found: ${command.stepId}")
                val completedStep = step.markCompleted()
                taskStepRepository?.save(completedStep)
                updateWorkingMemorySteps(step.taskId)
                state
            }

            // Memory management commands
            is Command.ClearMemory -> {
                val level = currentLevel(state)
                val taskId = state.currentTodoTaskId?.let { ModelTaskId(it) }
                memoryService?.clearSession(level, taskId)
                state
            }

            is Command.ShowStatus -> {
                val level = currentLevel(state)
                val taskId = state.currentTodoTaskId?.let { ModelTaskId(it) }
                memoryService?.getMemoryStatus(level, taskId)
                state
            }

            // LTM (Long-Term Memory) commands
            is Command.SaveFact -> {
                val fact = Fact(
                    id = FactId(UUID.randomUUID().toString()),
                    content = command.content,
                    createdAt = Instant.now()
                )
                factRepository?.save(fact)
                state
            }

            is Command.ListLtmFacts -> {
                // Rendering handled by CliApplication
                state
            }

            is Command.ForgetFact -> {
                val factId = FactId(command.factId)
                val exists = factRepository?.findById(factId) != null
                if (exists) {
                    factRepository?.delete(factId)
                }
                state
            }

            is Command.SearchFacts -> {
                // Rendering handled by CliApplication
                state
            }

            // LLM integration commands
            is Command.Plan -> {
                // Обработка FSM-команды :plan будет в CliApplication
                // (требует многострочного ввода для описания задачи)
                state
            }

            is Command.PlanSteps -> state

            // Profile management commands (PM)
            is Command.ProfileNew -> {
                // Обработка создания профиля будет в CliApplication
                // (требует многострочного ввода)
                state
            }

            is Command.ProfileList -> {
                // Рендеринг будет в CliApplication
                state
            }

            is Command.ProfileUse -> {
                if (command.name == "none") {
                    getTask2Executor()?.handleDeactivateProfile()
                } else {
                    getTask2Executor()?.handleActivateByName(command.name)
                }
                state
            }

            is Command.ProfileEdit -> {
                // Обработка редактирования профиля будет в CliApplication
                // (требует многострочного ввода)
                state
            }

            is Command.ProfileDelete -> {
                // Обработка удаления профиля будет в CliApplication
                state
            }

            is Command.ProfileShow -> {
                // Рендеринг будет в CliApplication
                state
            }

            is Command.Debug -> {
                // Обработка команды :debug будет в CliApplication
                // (требует рендеринга результата)
                state
            }

            is Command.ShowState -> {
                // Обработка команды :state будет в CliApplication
                // (требует доступа к CommandEngine для получения состояния FSM)
                state
            }

            is Command.Abort -> {
                // Обработка команды :abort будет в CliApplication
                // (требует подтверждения и вызова CommandEngine.abortCommand())
                state
            }

            is Command.InvariantAdd -> state
            is Command.InvariantList -> state
            is Command.InvariantRemove -> state

            is Command.UserInput -> state
            is Command.Unknown -> state
        }
    }

    /**
     * Определить текущий уровень сессии на основе состояния CLI.
     */
    private fun currentLevel(state: CliState): SessionLevel {
        return if (state.currentTodoTaskId != null) {
            SessionLevel.TASK_DETAIL
        } else {
            SessionLevel.TASK_LIST
        }
    }

    /**
     * Проверяет, что открыта задача (уровень TASK_DETAIL).
     * Бросает [IllegalStateException] с сообщением "No task is currently open",
     * если задача не открыта.
     */
    private fun requireTaskOpen(state: CliState) {
        require(state.currentTodoTaskId != null) {
            "No task is currently open"
        }
    }

    /**
     * Обновляет WorkingMemory для уровня задачи, загружая актуальный список шагов
     * из [taskStepRepository] и переключая STM на уровень задачи.
     */
    private suspend fun updateWorkingMemorySteps(taskId: ModelTaskId) {
        val steps = taskStepRepository?.findByTaskId(taskId) ?: emptyList()
        memoryService?.switchToTaskLevel(taskId)
    }

    suspend fun executeUserInput(command: Command.UserInput, state: CliState): Pair<CliState, TaskResult?> {
        var currentState = state
        var taskId = currentState.currentTaskId

        if (taskId == null && executors.size == 1) {
            taskId = executors.keys.first().value
            currentState = currentState.copy(currentTaskId = taskId)
        }

        if (taskId == null) return Pair(currentState, null)

        val executor = executors[TaskId(taskId)] ?: return Pair(currentState, null)

        val prompt = Prompt(command.text)
        val result = executor.execute(prompt, currentState.executionConfig)

        return Pair(currentState, result)
    }

    fun getExecutor(taskId: TaskId): TaskExecutor? = executors[taskId]

    /**
     * Возвращает [Task2Executor] из карты executors, если он зарегистрирован.
     * Task2Executor имеет ID = 2.
     */
    fun getTask2Executor(): Task2Executor? = executors[TaskId(2)] as? Task2Executor

    fun getAllExecutors(): List<TaskExecutor> = executors.values.toList()
}

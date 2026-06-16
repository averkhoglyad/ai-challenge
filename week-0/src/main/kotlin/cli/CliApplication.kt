package io.averkhogliad.ai.challenge.week0.cli

import io.averkhogliad.ai.challenge.week0.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week0.cli.commands.Command
import io.averkhogliad.ai.challenge.week0.cli.commands.CommandContext
import io.averkhogliad.ai.challenge.week0.cli.commands.CommandParser
import io.averkhogliad.ai.challenge.week0.domain.TaskId
import io.averkhogliad.ai.challenge.week0.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week0.domain.service.ResourceManager
import kotlinx.coroutines.runBlocking

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
 * @param llmPort порт для взаимодействия с LLM (используется для получения списка моделей)
 * @param resourceManager порт для управления ресурсами (закрывается при завершении работы)
 */
class CliApplication(
    private val executors: Map<TaskId, TaskExecutor>,
    private val renderer: CliRenderer = ConsoleCliRenderer(),
    private val llmPort: LlmPort? = null,
    private val resourceManager: ResourceManager? = null
) : AutoCloseable {

    private val handler = CommandHandler(executors)

    fun run(args: Array<String>) {
        runBlocking {
            repl()
        }
    }

    /**
     * Освобождает ресурсы приложения.
     *
     * Делегирует [resourceManager.close()] для освобождения HTTP-соединений и пулов потоков.
     * Вызывается автоматически при использовании `use {}`.
     */
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
     *
     * Логика:
     * - Команды, меняющие состояние → handler.handle() + опциональный рендеринг
     * - UserInput → handler.executeUserInput() + renderResult
     * - Render-only команды → только рендеринг (состояние не меняется)
     */
    private suspend fun handleCommandWithRendering(command: Command, state: CliState): CliState {
        return when (command) {
            // ═══════════════════════════════════════════════════════
            // Render-only команды
            // ═══════════════════════════════════════════════════════
            is Command.Help -> {
                renderer.renderHelp(state)
                state
            }

            is Command.ShowParameters -> {
                renderer.renderParameters(state)
                state
            }

            is Command.ShowConfig -> {
                renderer.renderTask3Config(state)
                state
            }

            is Command.ShowModels -> {
                val models = llmPort?.let { port ->
                    try {
                        port.listModels()
                    } catch (e: Exception) {
                        renderer.renderError("Ошибка получения списка моделей: ${e.message}")
                        emptyList()
                    }
                } ?: emptyList()
                renderer.renderAvailableModels(models)
                state
            }

            is Command.Unknown -> {
                renderer.renderError("Неизвестная команда: ${command.raw}")
                state
            }

            // ═══════════════════════════════════════════════════════
            // Навигационные команды
            // ═══════════════════════════════════════════════════════
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
                renderer.renderMenu(handler.getAllExecutors())
                newState
            }

            is Command.Quit -> {
                handler.handle(command, state)
            }

            // ═══════════════════════════════════════════════════════
            // UserInput — выполнение задачи
            // ═══════════════════════════════════════════════════════
            is Command.UserInput -> {
                // Показываем отправляемый промпт и параметры
                println()
                println("═══ Отправка запроса ═══")
                println("Промпт: ${command.text}")
                println("Параметры: температура=${state.executionConfig.temperature}, maxTokens=${state.executionConfig.maxTokens}")
                if (state.executionConfig.modelId != null) {
                    println("Модель: ${state.executionConfig.modelId!!.value}")
                }
                println("═══════════════════════")
                println()
                renderer.renderLoadingStart("Отправка запроса...")
                val (newState, result) = handler.executeUserInput(command, state)
                renderer.renderLoadingStop()
                when (result) {
                    is io.averkhogliad.ai.challenge.week0.domain.TaskResult.Success -> {
                        renderer.renderResult(result)
                    }

                    is io.averkhogliad.ai.challenge.week0.domain.TaskResult.Error -> {
                        renderer.renderError(result.message)
                    }

                    is io.averkhogliad.ai.challenge.week0.domain.TaskResult.Partial -> {
                        renderer.renderResult(result)
                    }

                    null -> {
                        // Нет активной задачи — показываем меню
                        renderer.renderMenu(handler.getAllExecutors())
                    }
                }
                newState
            }

            // ═══════════════════════════════════════════════════════
            // Команды параметров — обновление состояния
            // ═══════════════════════════════════════════════════════
            is Command.SetModels -> {
                // Преобразуем индексы моделей в ModelId и сохраняем в executionConfig
                val models = llmPort?.let { port ->
                    try {
                        port.listModels()
                    } catch (e: Exception) {
                        renderer.renderError("Ошибка получения списка моделей: ${e.message}")
                        emptyList()
                    }
                } ?: emptyList()

                val selectedModelIds = command.modelIndices
                    .filter { it in 1..models.size }
                    .map { models[it - 1] }

                val newState = state.copy(
                    task5SelectedModels = command.modelIndices,
                    executionConfig = state.executionConfig.copy(
                        task5 = state.executionConfig.task5.copy(modelIds = selectedModelIds)
                    )
                )
                renderer.renderResult(io.averkhogliad.ai.challenge.week0.domain.TaskResult.Success("Выбрано моделей: ${selectedModelIds.size}"))
                newState
            }

            else -> {
                // SetTemperature, SetMaxTokens, SetStopSequences, ResetParameters,
                // SetMode, SetStep, SetMeta, SetRole, SetExperts, ToggleSummary
                handler.handle(command, state)
            }
        }
    }

    /**
     * Строит [CommandContext] на основе текущего состояния CLI.
     */
    private fun buildCommandContext(state: CliState): CommandContext {
        val taskId = state.currentTaskId
        if (taskId == null) {
            return CommandContext.TASK_SELECTION
        }

        // Все задачи имеют базовый набор команд
        val commands = mutableSetOf(
            "help", "h", "quit", "q", "back", "b", "task", "t",
            "temp", "maxtokens", "reset", "params", "stop"
        )

        // Task3 специфичные команды
        if (taskId == 3) {
            commands.addAll(listOf("mode", "step", "meta", "role", "experts", "summary", "config"))
        }

        // Task5 специфичные команды
        if (taskId == 5) {
            commands.add("models")
        }

        return CommandContext(
            currentTaskId = taskId,
            availableCommands = commands
        )
    }
}

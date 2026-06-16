package io.averkhogliad.ai.challenge.week0.cli

import io.averkhogliad.ai.challenge.week0.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week0.domain.ModelId
import io.averkhogliad.ai.challenge.week0.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week0.domain.TaskResult

/**
 * Консольная реализация [CliRenderer] — вывод в System.out.
 *
 * Минималистичная реализация без зависимостей от Mordant/Terminal.
 * При необходимости может быть заменена на Mordant-based реализацию.
 */
class ConsoleCliRenderer : CliRenderer {

    // ──── Прелоадер (анимированный спиннер) ────

    @Volatile
    private var spinnerRunning = false
    private var spinnerThread: Thread? = null
    private var spinnerMessage: String = ""

    override fun renderLoadingStart(message: String) {
        if (spinnerRunning) return
        spinnerMessage = message
        spinnerRunning = true
        spinnerThread = Thread {
            val frames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
            var frameIndex = 0
            try {
                while (spinnerRunning) {
                    val frame = frames[frameIndex % frames.size]
                    print("\r  $frame $spinnerMessage")
                    System.out.flush()
                    frameIndex++
                    Thread.sleep(80)
                }
            } catch (_: InterruptedException) {
                // spinner stopped
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    override fun renderLoadingStop() {
        if (!spinnerRunning) return
        spinnerRunning = false
        spinnerThread?.interrupt()
        spinnerThread = null
        print("\r" + " ".repeat(spinnerMessage.length + 4) + "\r")
        System.out.flush()
    }

    override fun renderMenu(executors: List<TaskExecutor>) {
        println()
        println("=".repeat(60))
        println("  AI Challenge — Выбор задачи")
        println("=".repeat(60))
        for (executor in executors.sortedBy { it.taskId.value }) {
            val meta = executor.metadata
            println("  ${meta.id.value}. ${meta.title}")
            println("     ${meta.description}")
        }
        println("=".repeat(60))
    }

    override fun renderTaskHeader(metadata: TaskMetadata) {
        println()
        println("-".repeat(60))
        println("  ${metadata.title}")
        println("  ${metadata.description}")
        if (metadata.availableCommands.isNotEmpty()) {
            println("  Доступные команды: ${metadata.availableCommands.joinToString(", ")}")
        }
        println("-".repeat(60))
    }

    override fun renderResult(result: TaskResult) {
        when (result) {
            is TaskResult.Success -> {
                println()
                println(result.content)
                println()
            }

            is TaskResult.Error -> {
                renderError(result.message)
            }

            is TaskResult.Partial -> {
                print(result.content) // Без дополнительного перевода строки для потокового вывода
            }
        }
    }

    override fun renderError(message: String) {
        println()
        println("[ОШИБКА] $message")
        println()
    }

    override fun renderPrompt(state: CliState) {
        if (state.currentTaskId == null) {
            print("Выберите задачу (номер, 0=выход, :help=помощь): ")
        } else {
            print("prompt> ")
        }
    }

    override fun renderHelp(state: CliState) {
        println()
        if (state.currentTaskId == null) {
            println("Доступные команды:")
            println("  :help, :h     — эта справка")
            println("  :quit, :q     — выход из программы")
            println("  <номер>       — выбрать задачу по номеру")
            println("  0             — выход из программы")
        } else {
            println("Доступные команды:")
            println("  :help, :h     — эта справка")
            println("  :quit, :q     — выход из программы")
            println("  :back, :b     — вернуться к выбору задачи")
            println("  :temp [value] — установить температуру (0.0-2.0); без аргументов — показать")
            println("  :maxtokens [n]— установить макс. кол-во токенов; без аргументов — показать")
            println("  :stop [s1,s2] — установить стоп-последовательности; без аргументов — сбросить")
            println("  :reset        — сбросить все параметры к значениям по умолчанию")
            println("  :params       — показать текущие параметры")

            if (state.currentTaskId == 3) {
                println()
                println("  Команды Task3 (промпт-инжиниринг):")
                println("  :mode [mode]  — режим (direct/experts); без аргументов — показать конфигурацию")
                println("  :step [on/off]— пошаговый режим; без аргументов — показать")
                println("  :meta [on/off]— мета-анализ; без аргументов — показать")
                println("  :role [text]  — роль для LLM; без аргументов — показать")
                println("  :experts [e1,e2] — список экспертов; без аргументов — показать")
                println("  :summary [on/off] — суммаризация; без аргументов — показать")
                println("  :config       — показать конфигурацию Task3")
            }

            if (state.currentTaskId == 5) {
                println()
                println("  Команды Task5 (бенчмарк моделей):")
                println("  :models       — показать список моделей")
                println("  :models <1,2> — выбрать модели по индексам")
            }
        }
        println()
    }

    override fun renderParameters(state: CliState) {
        val config = state.executionConfig
        println()
        println("Текущие параметры:")
        println("  temperature: ${config.temperature}")
        println("  maxTokens:   ${config.maxTokens}")
        println("  stopSequences: ${if (config.stopSequences.isEmpty()) "нет" else config.stopSequences.joinToString(", ")}")
        println("  modelId:     ${config.modelId ?: "default"}")
        println()
    }

    override fun renderTask3Config(state: CliState) {
        val task3 = state.executionConfig.task3
        println()
        println("Конфигурация Task3 (промпт-инжиниринг):")
        println("  mode:    ${task3.mode.name.lowercase()}")
        println("  step:    ${if (task3.stepEnabled) "on" else "off"}")
        println("  meta:    ${if (task3.metaEnabled) "on" else "off"}")
        println("  role:    ${task3.role ?: "не задана"}")
        println("  experts: ${task3.experts.joinToString(", ")}")
        println("  summary: ${if (task3.summary) "on" else "off"}")
        println()
    }

    override fun renderAvailableModels(models: List<ModelId>) {
        println()
        println("Доступные модели:")
        models.forEachIndexed { index, model ->
            println("  ${index + 1}. ${model.value}")
        }
        println()
    }

    override fun renderWelcome() {
        println()
        println("Добро пожаловать в AI Challenge!")
        println("Введите :help для справки.")
        println()
    }

    override fun renderGoodbye() {
        println()
        println("До свидания!")
    }
}

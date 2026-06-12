package io.averkhogliad.ai.challenge.week1.cli

import io.averkhogliad.ai.challenge.week1.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week1.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week1.domain.TaskResult

/**
 * Консольная реализация [CliRenderer] — вывод в System.out.
 *
 * Минималистичная реализация без зависимостей от Mordant/Terminal.
 * При необходимости может быть заменена на Mordant-based реализацию.
 */
class ConsoleCliRenderer : CliRenderer {

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
                System.out.flush()
            }

            is TaskResult.Error -> {
                renderError(result.message)
            }

            is TaskResult.Partial -> {
                println()
                println(result.content)
                println()
                System.out.flush()
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
            print("Выберите задачу (1, 0=выход, :help=помощь): ")
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
            println("  :task, :t     — вернуться к выбору задачи")
            println("  1             — выбрать задачу по номеру")
            println("  0             — выход из программы")
        } else {
            println("Доступные команды:")
            println("  :help, :h     — эта справка")
            println("  :quit, :q     — выход из программы")
            println("  :back, :b     — вернуться к выбору задачи")
            println("  :task, :t     — вернуться к выбору задачи")
            println("  :temp <value> — установить температуру (0.0-2.0)")
            println("  :maxtokens <n>— установить максимальное количество токенов")
            println("  :stop <s1,s2> — установить стоп-последовательности")
            println("  :reset        — сбросить параметры")
            println("  :params       — показать текущие параметры")
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

    override fun renderRequestInfo(
        prompt: String,
        config: io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
    ) {
        println()
        println("═══ Отправка запроса ═══")
        println("Промпт: $prompt")
        println("Параметры: температура=${config.temperature}, maxTokens=${config.maxTokens}")
        if (config.modelId != null) {
            println("Модель: ${config.modelId.value}")
        }
        println("═══════════════════════")
        println()
    }
}

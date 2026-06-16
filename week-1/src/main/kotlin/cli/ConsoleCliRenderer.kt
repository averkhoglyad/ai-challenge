package io.averkhogliad.ai.challenge.week1.cli

import io.averkhogliad.ai.challenge.week1.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week1.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.model.*
import io.averkhogliad.ai.challenge.week1.domain.service.ChatRole

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
        // Очищаем строку спиннера
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
            println()
            println("Команды диалогов:")
            println("  :new [title]  — создать новый диалог (заголовок опционально)")
            println("  :list         — список всех диалогов")
            println("  :history [id] — показать историю сообщений диалога (по умолчанию — текущий)")
            println("  :switch <id>  — переключиться на диалог по ID")
            println("  :delete <id>  — удалить диалог по ID")
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

    override fun renderDialogList(dialogs: List<DialogSummary>) {
        println()
        println("═".repeat(60))
        println("  Список диалогов")
        println("═".repeat(60))
        if (dialogs.isEmpty()) {
            println("  (нет сохранённых диалогов)")
        } else {
            for (dialog in dialogs) {
                println("  [${dialog.id.value}] ${dialog.title}")
                val parts = mutableListOf<String>()
                parts.add("сообщений: ${dialog.messageCount}")
                val tagLine = buildTagStatsLine(dialog.tagStats)
                if (tagLine.isNotEmpty()) parts.add(tagLine)
                println("    ${parts.joinToString(", ")}, обновлён: ${dialog.updatedAt}")
            }
        }
        println("═".repeat(60))
        println()
    }

    override fun renderDialogHistory(dialog: Dialog) {
        println()
        println("─".repeat(60))
        println("  Диалог: ${dialog.title}")
        println("  ID: ${dialog.id.value}")
        println("  Сообщений: ${dialog.messageCount}")
        if (dialog.accumulatedSummary != null) {
            println("  Summary: ${dialog.accumulatedSummary.take(120)}...")
        }
        val stats = TagStats.fromMessageTags(dialog.messageTags)
        if (!stats.isEmpty) {
            println("  Маркеры: ${buildTagStatsLine(stats)}")
        }
        println("─".repeat(60))

        // Рендеринг сообщений с маркерами
        for ((index, message) in dialog.messages.withIndex()) {
            val tags = dialog.messageTags[index] ?: emptySet()
            val tagPrefix = if (tags.isNotEmpty()) {
                tags.joinToString(" ") { it.displayLabelCompact } + " "
            } else ""

            val roleLabel = when (message.role) {
                ChatRole.SYSTEM -> "[SYSTEM]"
                ChatRole.USER -> "[USER]"
                ChatRole.ASSISTANT -> "[ASSISTANT]"
            }
            println("$tagPrefix$roleLabel ${message.content}")
            println()
        }
        println("─".repeat(60))
    }

    override fun renderCurrentDialogInfo(dialogId: DialogId?) {
        if (dialogId != null) {
            println("  Текущий диалог: ${dialogId.value}")
        } else {
            println("  Диалог не выбран")
        }
    }

    private fun buildTagStatsLine(stats: TagStats): String {
        val parts = mutableListOf<String>()
        if (stats.compressed > 0) parts.add("${MessageTag.Compressed.emoji} сжато:${stats.compressed}")
        if (stats.factExtractions > 0) parts.add("${MessageTag.FactExtraction.emoji} фактов:${stats.factExtractions}")
        if (stats.branchPoints > 0) parts.add("${MessageTag.BranchPoint.emoji} бранчей:${stats.branchPoints}")
        if (stats.checkpoints > 0) parts.add("${MessageTag.Checkpoint.emoji} чекпоинтов:${stats.checkpoints}")
        return parts.joinToString(", ")
    }
}

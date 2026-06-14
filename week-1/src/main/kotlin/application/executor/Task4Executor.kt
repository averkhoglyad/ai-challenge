package io.averkhogliad.ai.challenge.week1.application.executor

import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskId
import io.averkhogliad.ai.challenge.week1.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfigProvider
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.context.DialogContextCompressor
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import io.averkhogliad.ai.challenge.week1.domain.service.DialogRepository
import io.averkhogliad.ai.challenge.week1.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week1.domain.telemetry.TokenUsage
import java.util.*

/**
 * Executor для Task 4: сравнительный анализ сжатия контекста диалога.
 *
 * Проводит сравнение эффективности сжатия контекста: один и тот же диалог
 * прогоняется в двух режимах — без сжатия и со сжатием. Для каждого шага
 * собираются метрики использования токенов, выводится сравнительная таблица.
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация domain-сервисов
 * - **Автономная работа** — не требует REPL-цикла, выполняется однократно
 * - **Сравнительный анализ** — замер токенов в двух режимах
 *
 * ## Логика работы
 * 1. Создаёт два диалога (или один с переключением режимов)
 * 2. Режим 1 (без сжатия): configProvider.setEnabled(false), прогоняет сценарий
 * 3. Режим 2 (со сжатием): configProvider.setEnabled(true), прогоняет сценарий
 * 4. Выводит сравнительную таблицу: шаг | full tokens | compressed tokens | экономия %
 * 5. Выводит итоговую экономию
 *
 * @property llmPort порт для взаимодействия с LLM
 * @property dialogRepository репозиторий для персистентного хранения диалогов
 * @property compressor стратегия сжатия контекста
 * @property configProvider провайдер конфигурации сжатия
 */
class Task4Executor(
    private val llmPort: LlmPort,
    private val dialogRepository: DialogRepository,
    private val compressor: DialogContextCompressor,
    private val configProvider: ContextCompressionConfigProvider
) : TaskExecutor {

    override val taskId: TaskId = TaskId(4)

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 4: Сравнительный анализ сжатия контекста",
        description = "Сравнение эффективности сжатия контекста диалога: " +
                "один и тот же диалог с компрессией и без.",
        availableCommands = listOf(":compression", ":comp")
    )

    /**
     * Предопределённый сценарий из 15-20 сообщений на тему разработки ПО.
     * Пользователь задаёт серию вопросов, постепенно углубляясь в тему.
     */
    private val scenarioMessages = listOf(
        "Что такое микросервисная архитектура?",
        "Какие преимущества у микросервисов перед монолитом?",
        "Какие недостатки у микросервисной архитектуры?",
        "Как организовать взаимодействие между микросервисами?",
        "Что такое API Gateway и зачем он нужен?",
        "Какие паттерны используются в микросервисах?",
        "Расскажи про паттерн Circuit Breaker",
        "Что такое Service Discovery?",
        "Как обеспечить отказоустойчивость микросервисов?",
        "Какие инструменты мониторинга используются?",
        "Что такое distributed tracing?",
        "Как управлять конфигурацией в микросервисах?",
        "Что такое eventual consistency?",
        "Как обеспечить транзакционную целостность?",
        "Какие базы данных лучше использовать для микросервисов?",
        "Что такое CQRS и Event Sourcing?",
        "Как тестировать микросервисы?",
        "Какие лучшие практики деплоя микросервисов?"
    )

    /**
     * Выполняет сравнительный анализ сжатия контекста.
     *
     * Запускает предопределённый сценарий в двух режимах (без сжатия и со сжатием),
     * собирает метрики токенов для каждого шага и выводит сравнительную таблицу.
     *
     * @param prompt не используется в Task4 (сценарий предопределён)
     * @param config конфигурация выполнения (используется для temperature, maxTokens)
     * @return [TaskResult.Success] с результатами сравнения в content
     */
    override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        // Save original state to restore after execution
        val originalEnabled = configProvider.get().enabled

        return try {
            // Режим 1: без сжатия
            configProvider.setEnabled(false)
            val noCompressionResults = runScenario(config, compressionEnabled = false)

            // Режим 2: со сжатием
            configProvider.setEnabled(true)
            val compressionResults = runScenario(config, compressionEnabled = true)

            // Формируем сравнительную таблицу
            val comparisonTable = buildComparisonTable(noCompressionResults, compressionResults)

            TaskResult.Success(
                content = comparisonTable,
                tokenUsage = calculateTotalSavings(noCompressionResults, compressionResults)
            )
        } catch (e: Exception) {
            TaskResult.Error(
                message = "Task 4 execution failed: ${e.message}",
                cause = e
            )
        } finally {
            // Restore original state
            configProvider.setEnabled(originalEnabled)
        }
    }

    /**
     * Запускает предопределённый сценарий и собирает метрики токенов для каждого шага.
     *
     * @param config конфигурация выполнения
     * @param compressionEnabled флаг включения сжатия
     * @return список метрик для каждого шага сценария
     */
    private suspend fun runScenario(
        config: TaskExecutionConfig,
        compressionEnabled: Boolean
    ): List<StepMetrics> {
        val dialogId = DialogId(UUID.randomUUID().toString())
        val dialog = Dialog.create(dialogId, "Task4 Scenario - ${if (compressionEnabled) "Compressed" else "Full"}")
        dialogRepository.save(dialog)

        val metrics = mutableListOf<StepMetrics>()
        var currentDialog = dialog

        for ((stepIndex, message) in scenarioMessages.withIndex()) {
            // Добавляем user message
            currentDialog = currentDialog.addUserMessage(message)

            // Получаем контекст для отправки в LLM
            val messages = currentDialog.messages
            val previousSummary = currentDialog.accumulatedSummary

            // Применяем сжатие если включено
            val compressionConfig = configProvider.get()
            val dialogContext = if (compressionEnabled && compressionConfig.enabled) {
                compressor.compress(messages, compressionConfig, previousSummary)
            } else {
                // Без сжатия — возвращаем все сообщения
                io.averkhogliad.ai.challenge.week1.domain.context.DialogContext(
                    summary = null,
                    recentMessages = messages,
                    compressedMessageCount = 0
                )
            }

            // Оцениваем количество токенов в контексте
            val inputTokens = dialogContext.estimateTokenCount()

            // Вызываем LLM
            val compressedUtilsMessages = dialogContext.toMessagesList("")
            val compressedMessages = compressedUtilsMessages.map { utilsMsg ->
                ChatMessage(
                    role = io.averkhogliad.ai.challenge.week1.domain.service.ChatRole.valueOf(utilsMsg.role.uppercase()),
                    content = utilsMsg.content
                )
            }

            val result = llmPort.chatWithMessages(compressedMessages, config)

            // Извлекаем token usage из результата
            val tokenUsage = when (result) {
                is TaskResult.Success -> result.tokenUsage
                is TaskResult.Partial -> result.tokenUsage
                is TaskResult.Error -> null
            }

            // Обновляем диалог
            when (result) {
                is TaskResult.Success -> {
                    tokenUsage?.let { currentDialog = currentDialog.addTokenUsage(it) }
                    dialogContext.summary?.let { currentDialog = currentDialog.updateAccumulatedSummary(it) }
                    currentDialog = currentDialog.addAssistantMessage(result.content)
                }

                is TaskResult.Partial -> {
                    dialogContext.summary?.let { currentDialog = currentDialog.updateAccumulatedSummary(it) }
                    currentDialog = currentDialog.addAssistantMessage(result.content)
                }

                is TaskResult.Error -> {
                    dialogContext.summary?.let { currentDialog = currentDialog.updateAccumulatedSummary(it) }
                }
            }

            dialogRepository.save(currentDialog)

            // Сохраняем метрики шага
            metrics.add(
                StepMetrics(
                    step = stepIndex + 1,
                    messagePreview = message.take(40) + if (message.length > 40) "..." else "",
                    inputTokens = inputTokens,
                    promptTokens = tokenUsage?.promptTokens ?: 0,
                    completionTokens = tokenUsage?.completionTokens ?: 0,
                    totalTokens = tokenUsage?.totalTokens ?: 0,
                    compressedMessageCount = dialogContext.compressedMessageCount
                )
            )
        }

        return metrics
    }

    /**
     * Строит сравнительную таблицу результатов двух режимов.
     *
     * @param noCompression метрики режима без сжатия
     * @param compression метрики режима со сжатием
     * @return отформатированная таблица сравнения
     */
    private fun buildComparisonTable(
        noCompression: List<StepMetrics>,
        compression: List<StepMetrics>
    ): String {
        val separator = "=".repeat(100)
        val thinSeparator = "-".repeat(100)

        return buildString {
            appendLine(separator)
            appendLine("📊 СРАВНИТЕЛЬНЫЙ АНАЛИЗ СЖАТИЯ КОНТЕКСТА")
            appendLine(separator)
            appendLine()
            appendLine("Сценарий: ${scenarioMessages.size} сообщений на тему разработки ПО")
            appendLine()
            appendLine(thinSeparator)
            appendLine(
                String.format(
                    "%-6s | %-40s | %-12s | %-12s | %-10s",
                    "Шаг",
                    "Сообщение",
                    "Full Tokens",
                    "Compressed",
                    "Экономия"
                )
            )
            appendLine(thinSeparator)

            var totalFullTokens = 0
            var totalCompressedTokens = 0

            for (i in noCompression.indices) {
                val noComp = noCompression[i]
                val comp = compression.getOrNull(i) ?: continue

                val fullTokens = noComp.inputTokens
                val compressedTokens = comp.inputTokens
                val savings = if (fullTokens > 0) {
                    ((fullTokens - compressedTokens).toDouble() / fullTokens * 100)
                } else 0.0

                totalFullTokens += fullTokens
                totalCompressedTokens += compressedTokens

                appendLine(
                    String.format(
                        "%-6d | %-40s | %-12d | %-12d | %6.1f%%",
                        noComp.step,
                        noComp.messagePreview,
                        fullTokens,
                        compressedTokens,
                        savings
                    )
                )
            }

            appendLine(thinSeparator)
            appendLine()
            appendLine("📈 ИТОГОВАЯ СТАТИСТИКА:")
            appendLine("   Всего токенов (без сжатия):  $totalFullTokens")
            appendLine("   Всего токенов (со сжатием):  $totalCompressedTokens")

            val totalSavings = if (totalFullTokens > 0) {
                ((totalFullTokens - totalCompressedTokens).toDouble() / totalFullTokens * 100)
            } else 0.0

            appendLine("   Экономия:                    %.1f%%".format(totalSavings))
            appendLine()
            appendLine("💡 Вывод: Сжатие контекста позволяет значительно сократить использование токенов")
            appendLine("   при длинных диалогах, сохраняя при этом семантическую связность через summary.")
            appendLine(separator)
        }
    }

    /**
     * Вычисляет общую экономию токенов.
     *
     * @param noCompression метрики режима без сжатия
     * @param compression метрики режима со сжатием
     * @return TokenUsage с информацией об экономии
     */
    private fun calculateTotalSavings(
        noCompression: List<StepMetrics>,
        compression: List<StepMetrics>
    ): TokenUsage {
        val totalFullTokens = noCompression.sumOf { it.inputTokens }
        val totalCompressedTokens = compression.sumOf { it.inputTokens }
        val savedTokens = totalFullTokens - totalCompressedTokens

        return TokenUsage(
            promptTokens = savedTokens,
            completionTokens = 0,
            totalTokens = savedTokens
        )
    }

    /**
     * Метрики одного шага сценария.
     *
     * @property step номер шага (1-based)
     * @property messagePreview превью сообщения пользователя
     * @property inputTokens количество входных токенов (оценка контекста)
     * @property promptTokens количество prompt токенов из LLM ответа
     * @property completionTokens количество completion токенов из LLM ответа
     * @property totalTokens общее количество токенов из LLM ответа
     * @property compressedMessageCount количество сжатых сообщений
     */
    private data class StepMetrics(
        val step: Int,
        val messagePreview: String,
        val inputTokens: Int,
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
        val compressedMessageCount: Int
    )
}

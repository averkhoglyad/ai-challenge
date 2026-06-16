package io.averkhogliad.ai.challenge.week1.application.executor

import io.averkhogliad.ai.challenge.week1.application.DialogManager
import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskId
import io.averkhogliad.ai.challenge.week1.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.context.SlidingWindowCompressor
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.model.MessageTag
import io.averkhogliad.ai.challenge.week1.domain.service.DialogRepository
import io.averkhogliad.ai.challenge.week1.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week1.domain.strategy.*
import java.util.*

/**
 * Executor для Task 5: сравнение стратегий управления контекстом.
 *
 * Поддерживает два режима работы:
 * - **Режим сравнения**: тестирует три стратегии (Sliding Window, Sticky Facts, Branching)
 *   на едином сценарии из 15 сообщений и готовит сравнительный отчёт.
 * - **Диалоговый режим**: когда выбран активный диалог, обрабатывает каждое сообщение
 *   через активную стратегию [ContextStrategyManager], с сохранением истории в SQLite.
 *
 * @property llmPort порт для взаимодействия с LLM
 * @property dialogRepository репозиторий для хранения диалогов
 * @property slidingWindowCompressor компрессор для SlidingWindow стратегии
 * @property dialogManager менеджер для управления диалогами
 * @property contextStrategyManager менеджер стратегий управления контекстом
 */
class Task5Executor(
    private val llmPort: LlmPort,
    private val dialogRepository: DialogRepository,
    private val slidingWindowCompressor: SlidingWindowCompressor,
    private val dialogManager: DialogManager,
    private val contextStrategyManager: ContextStrategyManager
) : TaskExecutor, DialogManagerAccessor {

    override val taskId: TaskId = TaskId(5)

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 5: Стратегии управления контекстом",
        description = "Сравнение трёх стратегий управления контекстом: " +
                "Sliding Window, Sticky Facts и Branching. " +
                "Поддерживает интерактивные диалоги с командами :new, :list, :delete, :switch.",
        availableCommands = listOf(":strategy", ":facts", ":branch", ":new", ":list", ":history", ":delete", ":switch")
    )

    // ═══════════════════════════════════════════════════════════════
    // Состояние диалога (DialogManagerAccessor)
    // ═══════════════════════════════════════════════════════════════

    /** ID текущего активного диалога (null — диалог не выбран) */
    private var currentDialogId: DialogId? = null

    // ═══════════════════════════════════════════════════════════════
    // DialogManagerAccessor implementation
    // ═══════════════════════════════════════════════════════════════

    override fun getDialogManager(): DialogManager = dialogManager

    override fun getCurrentDialogId(): DialogId? = currentDialogId

    override fun setCurrentDialog(id: DialogId) {
        currentDialogId = id
    }

    override suspend fun createNewDialog(title: String): DialogId {
        val dialog = dialogManager.createNewDialog(title)
        currentDialogId = dialog.id
        return dialog.id
    }

    // Тестовый сценарий: сбор технического задания на разработку API
    private val scenarioMessages = listOf(
        "Мне нужно разработать REST API для сервиса управления пользователями",
        "API должен поддерживать CRUD операции для пользователей",
        "Обязательное требование: аутентификация через JWT токены",
        "Предпочитаю использовать Spring Boot и Kotlin",
        "База данных — PostgreSQL, но хочу рассмотреть alternatives",
        "Нужна пагинация для списков пользователей",
        "Важно: поддержка фильтрации и сортировки",
        "Давай обсудим структуру эндпоинтов",
        "Какие HTTP методы лучше использовать для каждой операции?",
        "Нужна ли версионность API?",
        "Как лучше организовать обработку ошибок?",
        "Требование: логирование всех запросов",
        "Предпочтение: использовать OpenAPI спецификацию",
        "Давай вернёмся к вопросу аутентификации",
        "Итог: какие основные компоненты системы мы определили?"
    )

    override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        // Диалоговый режим: обрабатываем сообщение через активную стратегию
        if (currentDialogId != null) {
            return executeDialogMode(prompt, config)
        }

        // Режим сравнения: прогоняем сценарий через все стратегии
        return executeComparisonMode(config)
    }

    // ═══════════════════════════════════════════════════════════════
    // Диалоговый режим
    // ═══════════════════════════════════════════════════════════════

    /**
     * Обрабатывает пользовательский запрос в диалоговом режиме через активную стратегию.
     */
    private suspend fun executeDialogMode(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        return try {
            // 1. Загружаем диалог
            var dialog = dialogRepository.findById(currentDialogId!!)
                ?: Dialog.create(currentDialogId!!, "Dialog ${currentDialogId!!.value.take(8)}")

            // 2. Добавляем user message
            dialog = dialog.addUserMessage(prompt.value)

            // 3. Обрабатываем сообщение через активную стратегию
            val strategy = contextStrategyManager.getCurrentStrategy()
            val userMessageIndex = dialog.messages.size - 1  // индекс только что добавленного user message
            val actionResult = strategy.processUserMessage(dialog, prompt.value, ContextManagementConfig())

            // Помечаем сообщения тегами на основе actionResult
            actionResult.actionsPerformed.forEach { action ->
                when (action) {
                    is StrategyAction.FactsExtracted -> dialog =
                        dialog.tagMessages(MessageTag.FactExtraction, userMessageIndex)

                    is StrategyAction.CheckpointCreated -> dialog =
                        dialog.tagMessages(MessageTag.Checkpoint, userMessageIndex)

                    is StrategyAction.BranchCreated -> dialog =
                        dialog.tagMessages(MessageTag.BranchPoint, userMessageIndex)

                    is StrategyAction.BranchSwitched -> {}  // переключение ветки не тегирует сообщения
                    is StrategyAction.FactsUpdated -> dialog =
                        dialog.tagMessages(MessageTag.FactExtraction, userMessageIndex)
                }
            }

            // 4. Подготавливаем контекст для LLM
            val preparedContext = strategy.prepareContext(
                dialog,
                "You are a helpful assistant.",
                ContextManagementConfig()
            )

            // Помечаем сжатые сообщения тегом Compressed (для SlidingWindow стратегии)
            val compressedMsgCount = (preparedContext.metadata["compressedMessageCount"] as? Int) ?: 0
            if (compressedMsgCount > 0) {
                val compressedIndices = (0 until compressedMsgCount).toList().toIntArray()
                dialog = dialog.tagMessages(MessageTag.Compressed, *compressedIndices)
            }

            // 5. Вызываем LLM
            val llmResult = llmPort.chatWithMessages(preparedContext.messages, config)

            // Сохраняем новый accumulatedSummary из preparedContext (инкрементальная компрессия)
            dialog = applyAccumulatedSummaryIfPresent(dialog, preparedContext)

            // 6. Сохраняем результат
            when (llmResult) {
                is TaskResult.Success -> {
                    llmResult.tokenUsage?.let { dialog = dialog.addTokenUsage(it) }
                    dialog = dialog.addAssistantMessage(llmResult.content)
                    dialogRepository.save(dialog)
                }

                is TaskResult.Partial -> {
                    dialog = dialog.addAssistantMessage(llmResult.content)
                    dialogRepository.save(dialog)
                }

                is TaskResult.Error -> {
                    dialogRepository.save(dialog)
                }
            }

            enrichWithStrategyInfo(llmResult, preparedContext, strategy, actionResult)
        } catch (e: Exception) {
            TaskResult.Error(
                message = "Task 5 dialog mode failed: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Обогащает результат информацией о текущей стратегии и размере контекста.
     */
    private fun enrichWithStrategyInfo(
        result: TaskResult,
        preparedContext: PreparedContext,
        strategy: ContextManagementStrategy,
        actionResult: StrategyActionResult
    ): TaskResult {
        val contextBlock = buildStrategyInfoBlock(strategy, preparedContext, actionResult)

        return when (result) {
            is TaskResult.Success -> result.copy(content = result.content + contextBlock)
            is TaskResult.Partial -> result.copy(content = result.content + contextBlock)
            is TaskResult.Error -> result
        }
    }

    /**
     * Формирует информационный блок о стратегии и размере контекста.
     */
    private fun buildStrategyInfoBlock(
        strategy: ContextManagementStrategy,
        preparedContext: PreparedContext,
        actionResult: StrategyActionResult
    ): String {
        val thinSep = "-".repeat(60)
        val strategyType = contextStrategyManager.getCurrentStrategyType()
        val messagesCount = preparedContext.messages.size
        val estimatedTokens = preparedContext.estimatedTokens
        val factsCount = actionResult.metadata["totalFacts"] as? Int ?: 0
        val checkpointCount = (actionResult.metadata["totalCheckpoints"] as? Int) ?: 0

        return buildString {
            appendLine()
            appendLine(thinSep)
            appendLine("🎯 Стратегия: ${strategy.name} [${strategyType.code}]")
            appendLine("   ${strategy.description}")
            appendLine("   Размер контекста: $messagesCount сообщ. (~$estimatedTokens токенов)")
            when (strategyType) {
                StrategyType.SLIDING_WINDOW -> {
                    appendLine("   Стратегия сохраняет последние N сообщений в скользящем окне.")
                }

                StrategyType.STICKY_FACTS -> {
                    appendLine("   Извлечено фактов: $factsCount")
                    appendLine("   Управление: :facts list, :facts add <key>=<value>, :facts remove <key>")
                }

                StrategyType.BRANCHING -> {
                    appendLine("   Чекпоинтов: $checkpointCount")
                    appendLine("   Управление: :branch list, :branch create <name>, :checkpoint")
                }
            }
            appendLine("   Смена стратегии: :strategy <1..3>")
            appendLine(thinSep)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Режим сравнения (автономный сценарий)
    // ═══════════════════════════════════════════════════════════════

    private suspend fun executeComparisonMode(config: TaskExecutionConfig): TaskResult {
        return try {
            // Сохраняем текущую стратегию для восстановления после теста
            val savedType = contextStrategyManager.getCurrentStrategyType()

            // Создаём временный менеджер для тестирования всех стратегий
            val testManager = ContextStrategyManager(llmPort, slidingWindowCompressor)

            // Тестируем каждую стратегию
            val results = mutableMapOf<StrategyType, StrategyTestResult>()

            for (strategyType in StrategyType.entries) {
                testManager.switchStrategy(strategyType)
                val result = testStrategy(testManager.getCurrentStrategy(), strategyType, config)
                results[strategyType] = result
            }

            // Восстанавливаем стратегию активного диалога
            contextStrategyManager.switchStrategy(savedType)

            // Формируем сравнительный отчёт
            val report = buildComparisonReport(results)

            TaskResult.Success(
                content = report,
                tokenUsage = calculateTotalTokenUsage(results)
            )
        } catch (e: Exception) {
            TaskResult.Error(
                message = "Task 5 execution failed: ${e.message}",
                cause = e
            )
        }
    }

    private suspend fun testStrategy(
        strategy: ContextManagementStrategy,
        strategyType: StrategyType,
        config: TaskExecutionConfig
    ): StrategyTestResult {
        val dialogId = DialogId(UUID.randomUUID().toString())
        var dialog = Dialog.create(dialogId, "Task5 Test - ${strategyType.code}")
        dialogRepository.save(dialog)

        val metrics = mutableListOf<StepMetric>()
        var totalInputTokens = 0
        var totalOutputTokens = 0
        var totalFactsExtracted = 0
        var totalCheckpointsCreated = 0

        for ((index, userMessage) in scenarioMessages.withIndex()) {
            // Добавляем сообщение пользователя в диалог
            dialog = dialog.addUserMessage(userMessage)

            // Обрабатываем сообщение через стратегию
            val actionResult = strategy.processUserMessage(dialog, userMessage, ContextManagementConfig())

            // Считаем метрики действий
            actionResult.actionsPerformed.forEach { action ->
                when (action) {
                    is StrategyAction.FactsExtracted -> totalFactsExtracted += action.factsCount
                    is StrategyAction.CheckpointCreated -> totalCheckpointsCreated++
                    else -> {}
                }
            }

            // Подготавливаем контекст
            val preparedContext =
                strategy.prepareContext(dialog, "You are a helpful assistant.", ContextManagementConfig())

            // Помечаем сжатые сообщения тегом Compressed (для SlidingWindow стратегии)
            val compressedMsgCount = (preparedContext.metadata["compressedMessageCount"] as? Int) ?: 0
            if (compressedMsgCount > 0) {
                val compressedIndices = (0 until compressedMsgCount).toList().toIntArray()
                dialog = dialog.tagMessages(MessageTag.Compressed, *compressedIndices)
            }

            // Вызываем LLM
            val startTime = System.currentTimeMillis()
            val llmResult = llmPort.chatWithMessages(preparedContext.messages, config)
            val responseTime = System.currentTimeMillis() - startTime

            // Извлекаем метрики токенов
            val tokenUsage = when (llmResult) {
                is TaskResult.Success -> llmResult.tokenUsage
                is TaskResult.Partial -> llmResult.tokenUsage
                is TaskResult.Error -> null
            }

            val inputTokens = tokenUsage?.promptTokens ?: preparedContext.estimatedTokens
            val outputTokens = tokenUsage?.completionTokens ?: 0

            totalInputTokens += inputTokens
            totalOutputTokens += outputTokens

            // Сохраняем новый accumulatedSummary из preparedContext (инкрементальная компрессия)
            dialog = applyAccumulatedSummaryIfPresent(dialog, preparedContext)

            // Добавляем ответ ассистента в диалог
            val assistantResponse = when (llmResult) {
                is TaskResult.Success -> llmResult.content
                is TaskResult.Partial -> llmResult.content
                is TaskResult.Error -> "[Error: ${llmResult.message}]"
            }
            dialog = dialog.addAssistantMessage(assistantResponse)

            // Сохраняем метрики шага
            metrics.add(
                StepMetric(
                    step = index + 1,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    responseTimeMs = responseTime,
                    factsCount = (actionResult.metadata["totalFacts"] as? Int) ?: 0,
                    checkpointCreated = actionResult.actionsPerformed.any { it is StrategyAction.CheckpointCreated }
                )
            )

            dialogRepository.save(dialog)
        }

        return StrategyTestResult(
            strategyType = strategyType,
            totalInputTokens = totalInputTokens,
            totalOutputTokens = totalOutputTokens,
            totalFactsExtracted = totalFactsExtracted,
            totalCheckpointsCreated = totalCheckpointsCreated,
            stepMetrics = metrics,
            finalDialog = dialog
        )
    }

    private fun buildComparisonReport(results: Map<StrategyType, StrategyTestResult>): String {
        val separator = "=".repeat(100)
        val thinSeparator = "-".repeat(100)

        return buildString {
            appendLine(separator)
            appendLine("📊 СРАВНИТЕЛЬНЫЙ АНАЛИЗ СТРАТЕГИЙ УПРАВЛЕНИЯ КОНТЕКСТОМ")
            appendLine(separator)
            appendLine()
            appendLine("Сценарий: Сбор технического задания (${scenarioMessages.size} сообщений)")
            appendLine()
            appendLine(thinSeparator)

            // Таблица сравнения
            appendLine(
                String.format(
                    "%-20s | %-15s | %-15s | %-12s | %-12s | %-10s",
                    "Стратегия",
                    "Input Tokens",
                    "Output Tokens",
                    "Facts",
                    "Checkpoints",
                    "Avg Time"
                )
            )
            appendLine(thinSeparator)

            var grandTotalInput = 0
            var grandTotalOutput = 0
            var grandTotalFacts = 0
            var grandTotalCheckpoints = 0

            for ((type, result) in results) {
                val avgTime = result.stepMetrics.map { it.responseTimeMs }.average()

                grandTotalInput += result.totalInputTokens
                grandTotalOutput += result.totalOutputTokens
                grandTotalFacts += result.totalFactsExtracted
                grandTotalCheckpoints += result.totalCheckpointsCreated

                appendLine(
                    String.format(
                        "%-20s | %-15d | %-15d | %-12d | %-12d | %6.0fms",
                        result.strategyType.code,
                        result.totalInputTokens,
                        result.totalOutputTokens,
                        result.totalFactsExtracted,
                        result.totalCheckpointsCreated,
                        avgTime
                    )
                )
            }

            appendLine(thinSeparator)
            appendLine()

            // Детальная статистика по каждой стратегии
            appendLine("📈 ДЕТАЛЬНАЯ СТАТИСТИКА:")
            appendLine()

            for ((type, result) in results) {
                appendLine("【${result.strategyType.name}】")
                appendLine("  • Всего входных токенов: ${result.totalInputTokens}")
                appendLine("  • Всего выходных токенов: ${result.totalOutputTokens}")
                appendLine("  • Извлечено фактов: ${result.totalFactsExtracted}")
                appendLine("  • Создано чекпоинтов: ${result.totalCheckpointsCreated}")
                appendLine(
                    "  • Среднее время отклика: %.0fms".format(
                        result.stepMetrics.map { it.responseTimeMs }.average()
                    )
                )
                appendLine()
            }

            appendLine(thinSeparator)
            appendLine()

            // Рекомендации
            appendLine("💡 РЕКОМЕНДАЦИИ:")
            appendLine()
            appendLine("• Sliding Window:")
            appendLine("  - Лучше для коротких диалогов без важных деталей")
            appendLine("  - Минимальный расход токенов")
            appendLine("  - Быстрый отклик")
            appendLine()
            appendLine("• Sticky Facts:")
            appendLine("  - Оптимален для сбора требований и ТЗ")
            appendLine("  - Сохраняет ключевые факты")
            appendLine("  - Дополнительные вызовы LLM для извлечения фактов")
            appendLine()
            appendLine("• Branching:")
            appendLine("  - Полезен при исследовании разных вариантов")
            appendLine("  - Позволяет вернуться к предыдущим точкам")
            appendLine("  - Требует активного управления ветками")
            appendLine()
            appendLine("💬 Совет: используйте :new <название> чтобы создать диалог и начать интерактивную беседу.")
            appendLine(separator)
        }
    }

    private fun calculateTotalTokenUsage(results: Map<StrategyType, StrategyTestResult>): io.averkhogliad.ai.challenge.week1.domain.telemetry.TokenUsage {
        val totalInput = results.values.sumOf { it.totalInputTokens }
        val totalOutput = results.values.sumOf { it.totalOutputTokens }

        return io.averkhogliad.ai.challenge.week1.domain.telemetry.TokenUsage(
            promptTokens = totalInput,
            completionTokens = totalOutput,
            totalTokens = totalInput + totalOutput
        )
    }

    /**
     * Применяет накопленный [Dialog.accumulatedSummary] из [PreparedContext.metadata],
     * если ключ `"newAccumulatedSummary"` содержит непустую строку.
     *
     * Контракт: значение должно быть [String]; при несоответствии типа выбрасывается
     * [IllegalStateException] с читаемым сообщением.
     */
    private fun applyAccumulatedSummaryIfPresent(dialog: Dialog, preparedContext: PreparedContext): Dialog {
        val raw = preparedContext.metadata["newAccumulatedSummary"] ?: return dialog
        val newSummary = raw as? String
            ?: error("Expected String for metadata key 'newAccumulatedSummary', got ${raw::class.simpleName}")
        return if (newSummary.isNotBlank()) dialog.updateAccumulatedSummary(newSummary) else dialog
    }

    private data class StrategyTestResult(
        val strategyType: StrategyType,
        val totalInputTokens: Int,
        val totalOutputTokens: Int,
        val totalFactsExtracted: Int,
        val totalCheckpointsCreated: Int,
        val stepMetrics: List<StepMetric>,
        val finalDialog: Dialog
    )

    private data class StepMetric(
        val step: Int,
        val inputTokens: Int,
        val outputTokens: Int,
        val responseTimeMs: Long,
        val factsCount: Int,
        val checkpointCreated: Boolean
    )
}

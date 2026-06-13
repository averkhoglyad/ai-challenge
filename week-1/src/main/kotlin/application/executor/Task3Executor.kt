package io.averkhogliad.ai.challenge.week1.application.executor

import io.averkhogliad.ai.challenge.utils.llm.ModelInfo
import io.averkhogliad.ai.challenge.week1.application.DialogManager
import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskId
import io.averkhogliad.ai.challenge.week1.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.service.ConversationalAgent
import io.averkhogliad.ai.challenge.week1.domain.telemetry.CostEstimate
import io.averkhogliad.ai.challenge.week1.domain.telemetry.TokenTelemetry
import io.averkhogliad.ai.challenge.week1.domain.telemetry.TokenUsage

/**
 * Executor для Task 3: телеметрия токенов при общении с агентом.
 *
 * Работает как [Task2Executor] — делегирует выполнение в [ConversationalAgent],
 * использует CLI-инфраструктуру ([CliApplication]/[CommandHandler]) для REPL-цикла.
 * Единственное отличие: после каждого ответа агента в результат встраивается
 * блок телеметрии токенов (prompt, completion, кумулятивный контекст, стоимость,
 * процент заполнения контекстного окна).
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация domain-сервисов
 * - **Хранит состояние** — управляет текущим активным диалогом ([currentDialogId])
 * - **Не содержит REPL-цикл** — делегирует CLI-инфраструктуре
 * - **Не содержит прямого консольного вывода** — телеметрия встраивается в [TaskResult.content]
 *
 * ## Паттерн (идентичен Task2Executor)
 * ```
 * User Input → CommandHandler.executeUserInput() → executor.execute(prompt, config)
 *     → agent.process(prompt, config, dialogId)
 *     → обогащение результата телеметрией
 *     → TaskResult.Success (content = ответ агента + блок телеметрии)
 * ```
 *
 * @property agent ConversationalAgent с поддержкой персистентных диалогов
 * @property dialogManager менеджер для управления диалогами
 */
class Task3Executor(
    private val agent: ConversationalAgent,
    private val dialogManager: DialogManager,
    private val modelInfo: ModelInfo,
    private val contextWindow: Int = 16384
) : TaskExecutor, DialogManagerAccessor {

    override val taskId: TaskId = TaskId(3)

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 3: Телеметрия токенов",
        description = "Общение с агентом с отображением телеметрии токенов " +
                "после каждого ответа.",
        availableCommands = listOf(":new", ":list", ":delete", ":switch")
    )

    // ──── Состояние диалога ────

    /** ID текущего активного диалога (null — диалог не выбран) */
    private var currentDialogId: DialogId? = null

    /** Предыдущая агрегированная телеметрия (для накопления статистики) */
    private var previousTelemetry: TokenTelemetry? = null

    /**
     * Аккумулированная история токенов диалога.
     * Используется для корректного расчёта заполнения контекстного окна
     * (все промпт-токены истории, кроме текущего).
     */
    private var accumulatedHistoryTokens: Int = 0

    // ──── Основной метод execute ────

    /**
     * Выполняет запрос пользователя через [ConversationalAgent] и обогащает
     * результат блоком телеметрии токенов.
     *
     * Паттерн идентичен [Task2Executor.execute]:
     * - Если диалог не выбран, создаёт новый автоматически
     * - Делегирует выполнение в [agent.process]
     * - Обогащает [TaskResult.content] телеметрией
     *
     * @param prompt пользовательский промпт
     * @param config конфигурация выполнения
     * @return [TaskResult.Success] с обогащённым контентом, [TaskResult.Error] или [TaskResult.Partial]
     */
    override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        return try {
            // Если диалог не выбран, создаём новый автоматически
            val dialogId = currentDialogId ?: createNewDialog(
                prompt.value.take(30).ifBlank { "New Dialog" }
            )

            // Обрабатываем запрос через ConversationalAgent
            val result = agent.process(prompt, config, dialogId)

            // Обогащаем результат телеметрией
            enrichWithTelemetry(result, dialogId)
        } catch (e: Exception) {
            TaskResult.Error(
                message = "Task 3 execution failed: ${e.message}",
                cause = e
            )
        }
    }

    // ──── Управление диалогами (DialogManagerAccessor) ────

    /**
     * Устанавливает текущий активный диалог.
     * При смене диалога сбрасывает накопленную телеметрию.
     *
     * @param id идентификатор диалога
     */
    override fun setCurrentDialog(id: DialogId) {
        if (currentDialogId != id) {
            currentDialogId = id
            previousTelemetry = null
            accumulatedHistoryTokens = 0
        }
    }

    /**
     * Создаёт новый диалог и делает его активным.
     * Сбрасывает накопленную телеметрию.
     *
     * @param title название диалога
     * @return ID созданного диалога
     */
    override suspend fun createNewDialog(title: String): DialogId {
        val dialog = dialogManager.createNewDialog(title)
        currentDialogId = dialog.id
        previousTelemetry = null
        accumulatedHistoryTokens = 0
        return dialog.id
    }

    /**
     * Возвращает ID текущего активного диалога.
     *
     * @return ID текущего диалога или null, если диалог не выбран
     */
    override fun getCurrentDialogId(): DialogId? = currentDialogId

    /**
     * Возвращает [DialogManager] для доступа к операциям с диалогами.
     */
    override fun getDialogManager(): DialogManager = dialogManager

    // ──── Вспомогательные методы ────

    /**
     * Обогащает [TaskResult] блоком телеметрии токенов.
     *
     * Извлекает [TokenUsage] из результата, агрегирует с предыдущей телеметрией
     * и добавляет отформатированный блок телеметрии в [TaskResult.content].
     *
     * @param result исходный результат выполнения агента
     * @param dialogId идентификатор текущего диалога
     * @return обогащённый результат
     */
    private fun enrichWithTelemetry(result: TaskResult, dialogId: DialogId): TaskResult {
        // Извлекаем token usage из результата
        val tokenUsage: TokenUsage? = when (result) {
            is TaskResult.Success -> result.tokenUsage
            is TaskResult.Partial -> result.tokenUsage
            is TaskResult.Error -> result.tokenUsage
        }

        // Если нет данных о токенах — возвращаем результат как есть
        if (tokenUsage == null) return result

        // Агрегируем телеметрию
        val telemetry = TokenTelemetry.aggregate(
            previousTelemetry = previousTelemetry,
            currentStepUsage = tokenUsage,
            dialogHistoryTokens = accumulatedHistoryTokens,
            contextWindowLimit = contextWindow,
            costEstimate = CostEstimate.calculate(
                usage = tokenUsage,
                inputCostPerToken = (modelInfo.costPerMillionInputTokens ?: 0.0) / 1_000_000.0,
                outputCostPerToken = (modelInfo.costPerMillionOutputTokens ?: modelInfo.costPerMillionInputTokens
                ?: 0.0) / 1_000_000.0
            )
        )

        // Обновляем накопленное состояние
        previousTelemetry = telemetry
        accumulatedHistoryTokens += tokenUsage.promptTokens

        // Форматируем блок телеметрии
        val telemetryBlock = formatTelemetryBlock(telemetry)

        // Обогащаем контент результата
        return when (result) {
            is TaskResult.Success -> result.copy(
                content = result.content + "\n" + telemetryBlock
            )

            is TaskResult.Partial -> result.copy(
                content = result.content + "\n" + telemetryBlock
            )

            is TaskResult.Error -> result // Ошибки не обогащаем
        }
    }

    /**
     * Форматирует блок телеметрии для вывода в консоль.
     *
     * @param telemetry агрегированная телеметрия текущего шага
     * @return отформатированная строка с телеметрией
     */
    private fun formatTelemetryBlock(telemetry: TokenTelemetry): String {
        val thinSep = "-".repeat(70)

        val contextDisplay = telemetry.contextWindowLimit
            ?.let { limit -> "${telemetry.cumulativeUsage.totalTokens} / $limit токенов" }
            ?: "${telemetry.cumulativeUsage.totalTokens} / ? токенов"
        val overflow = if (telemetry.isContextOverflow) " ⚠ OVERFLOW" else ""

        val stepCostRub = (telemetry.costEstimate?.totalCost ?: 0.0)
            .let { "%.6f".format(it) }

        val cumulativeCost = CostEstimate.calculate(
            usage = telemetry.cumulativeUsage,
            inputCostPerToken = (modelInfo.costPerMillionInputTokens ?: 0.0) / 1_000_000.0,
            outputCostPerToken = (modelInfo.costPerMillionOutputTokens ?: modelInfo.costPerMillionInputTokens
            ?: 0.0) / 1_000_000.0
        )
        val cumulativeCostRub = (cumulativeCost.totalCost)
            .let { "%.6f".format(it) }

        return buildString {
            appendLine()
            appendLine(thinSep)
            appendLine("📊 Телеметрия:")
            appendLine("   Prompt токенов:       ${telemetry.stepUsage.promptTokens}")
            appendLine("   Completion токенов:   ${telemetry.stepUsage.completionTokens}")
            appendLine("   Всего за шаг:         ${telemetry.stepUsage.totalTokens}")
            appendLine("   Стоимость шага:       ${stepCostRub} ₽")
            appendLine("📈 Кумулятивная статистика:")
            appendLine("   Всего токенов:        ${telemetry.cumulativeUsage.totalTokens}")
            appendLine("   Общая стоимость:      ${cumulativeCostRub} ₽")
            appendLine("   Контекст:             $contextDisplay$overflow")
            appendLine(thinSep)
        }
    }
}

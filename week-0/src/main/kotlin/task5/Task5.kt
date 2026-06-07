package io.averkhogliad.ai.challenge.week0.task5

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.HorizontalRule
import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.llm.ChatParameters
import io.averkhogliad.ai.challenge.utils.llm.LlmClient
import io.averkhogliad.ai.challenge.utils.llm.LlmException
import io.averkhogliad.ai.challenge.utils.llm.ModelInfo
import io.averkhogliad.ai.challenge.utils.llm.loadModels
import io.averkhogliad.ai.challenge.week0.Task
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * Учебная задача #5: Сравнение производительности LLM моделей.
 *
 * Выполняет один и тот же запрос на нескольких моделях и сравнивает:
 * - Время ответа
 * - Количество токенов
 * - Стоимость (если модель платная)
 *
 * ## Интерактивные команды
 *
 * - `:models` — показать список доступных моделей
 * - `:models <idx1,idx2,...>` — выбрать модели по индексам
 * - `:maxTokens <value>` — установить лимит токенов
 * - `:reset` — сбросить параметры к значениям по умолчанию
 * - `:params` — показать текущую конфигурацию
 */
class Task5(
    private val config: Config,
    private val llmClient: LlmClient
) : Task {

    override val title: String = "Task 5: Сравнение производительности моделей"

    private val terminal = Terminal()

    /** Все доступные модели, загруженные из конфигурации */
    private val allModels: List<ModelInfo> = config.loadModels()

    /** Выбранные модели для бенчмарка (по умолчанию все) */
    private var selectedModels: List<ModelInfo> = allModels

    /** Максимальное количество токенов в ответе */
    private var maxTokens: Int = DEFAULT_MAX_TOKENS

    companion object {
        /** Значение max tokens по умолчанию */
        private const val DEFAULT_MAX_TOKENS = 500
    }

    override fun run(prompt: String) {
        if (selectedModels.isEmpty()) {
            terminal.println(red("✗ Список моделей пуст. Проверьте параметр 'models' в конфигурации."))
            terminal.println(gray("   Используйте :params для просмотра текущей конфигурации."))
            return
        }

        terminal.println()
        terminal.println(bold(cyan("🤖 Сравнение моделей для запроса: \"$prompt\"")))
        terminal.println()
        terminal.println(cyan("⏳ Отправляю запросы к ${selectedModels.size} моделям параллельно..."))
        terminal.println()

        // Фаза 1: Параллельный сбор результатов
        val results = runBlocking {
            coroutineScope {
                selectedModels.mapIndexed { index, modelInfo ->
                    async {
                        val result = benchmarkModel(modelInfo, prompt)
                        // Потокобезопасный прогресс-индикатор
                        synchronized(terminal) {
                            if (result.response != null) {
                                terminal.println(green("  ✓ [${index + 1}/${selectedModels.size}] ${modelInfo.name} — готово (${formatTime(result.responseTimeMs)})"))
                            } else {
                                terminal.println(red("  ✗ [${index + 1}/${selectedModels.size}] ${modelInfo.name} — ошибка"))
                            }
                        }
                        result
                    }
                }.awaitAll()
            }
        }

        terminal.println()

        // Фаза 2: Последовательный вывод детальных результатов
        for (result in results) {
            printDetailedResult(result)
        }

        // Итоговое сравнение
        printComparison(results)

        // Анализ и рекомендации
        printAnalysis(results)
    }

    /**
     * Выполняет бенчмарк одной модели: отправляет запрос и замеряет время.
     *
     * @param modelInfo Информация о модели
     * @param prompt Пользовательский промпт
     * @return Результат бенчмарка (успешный или с ошибкой)
     */
    private suspend fun benchmarkModel(modelInfo: ModelInfo, prompt: String): ModelBenchmarkResult {
        return try {
            val startTime = System.currentTimeMillis()
            val response = llmClient.chat(
                prompt,
                parameters = ChatParameters(maxTokens = maxTokens),
                model = modelInfo.modelId
            )
            val endTime = System.currentTimeMillis()
            val responseTimeMs = endTime - startTime

            val cost = modelInfo.calculateCost(
                promptTokens = response.usage?.promptTokens ?: 0,
                completionTokens = response.usage?.completionTokens ?: 0
            )

            ModelBenchmarkResult(modelInfo, response, responseTimeMs, cost)
        } catch (e: LlmException) {
            ModelBenchmarkResult(
                modelInfo = modelInfo,
                response = null,
                responseTimeMs = 0,
                estimatedCost = null,
                error = e.message
            )
        }
    }

    /**
     * Выводит детальный результат бенчмарка одной модели.
     */
    private fun printDetailedResult(result: ModelBenchmarkResult) {
        terminal.println(HorizontalRule(bold(cyan("Модель: ${result.modelInfo.name}"))))
        terminal.println(gray("   ID: ${result.modelInfo.modelId}"))

        if (result.modelInfo.costPer1kInputTokens != null) {
            terminal.println(cyan("   💰 Тариф: ${result.modelInfo.formatTariff()}"))
        }

        terminal.println()

        if (result.response != null) {
            terminal.println(green("✓ Время ответа: ${formatTime(result.responseTimeMs)}"))
            terminal.println(green("✓ Токенов: ${result.response.usage?.totalTokens ?: "N/A"}"))
            if (result.estimatedCost != null) {
                terminal.println(green("✓ Стоимость: \$${String.format(Locale.US, "%.6f", result.estimatedCost)}"))
            }
            if (result.response.finishReason == "length") {
                terminal.println(yellow("⚠️  Ответ обрезан: достигнут лимит maxTokens ($maxTokens)"))
            }
            terminal.println()
            terminal.println(bold(green("📝 Ответ модели:")))
            terminal.println(white(result.response.content))
        } else {
            terminal.println(red("✗ Ошибка при запросе к ${result.modelInfo.name}: ${result.error}"))
            terminal.println(yellow("⚠️  Модель недоступна"))
        }

        terminal.println()
    }

    /**
     * Выводит итоговое сравнение в виде таблицы.
     */
    private fun printComparison(results: List<ModelBenchmarkResult>) {
        terminal.println(HorizontalRule(bold(cyan("📊 Итоговое сравнение"))))
        terminal.println()

        // Таблица сравнения
        terminal.println("┌─────────────────────┬──────────┬──────────┬────────────┐")
        terminal.println("│ Модель              │ Время    │ Токены   │ Стоимость  │")
        terminal.println("├─────────────────────┼──────────┼──────────┼────────────┤")

        for (result in results) {
            val name = result.modelInfo.name.take(19).padEnd(19)
            val time = if (result.response != null) formatTime(result.responseTimeMs).padEnd(8) else "ОШИБКА".padEnd(8)
            val tokens = if (result.response != null) (result.response.usage?.totalTokens?.toString() ?: "N/A").padEnd(8) else "-".padEnd(8)
            val cost = if (result.estimatedCost != null) "\$${String.format(Locale.US, "%.6f", result.estimatedCost)}".padEnd(10) else "N/A".padEnd(10)

            terminal.println("│ $name │ $time │ $tokens │ $cost │")
        }

        terminal.println("└─────────────────────┴──────────┴──────────┴────────────┘")
        terminal.println()
    }

    /**
     * Выводит анализ результатов и рекомендации.
     */
    private fun printAnalysis(results: List<ModelBenchmarkResult>) {
        val successfulResults = results.filter { it.response != null }

        if (successfulResults.isEmpty()) {
            terminal.println(yellow("⚠️  Все модели недоступны. Проверьте:"))
            terminal.println(gray("   - API ключ в config/application.properties"))
            terminal.println(gray("   - Доступность сервиса"))
            terminal.println(gray("   - Rate limits"))
            return
        }

        terminal.println(bold(cyan("💡 Анализ:")))

        // Самая быстрая модель
        val fastest = successfulResults.minByOrNull { it.responseTimeMs }
        if (fastest != null) {
            terminal.println("- Самая быстрая: ${fastest.modelInfo.name} (${formatTime(fastest.responseTimeMs)})")
        }

        // Самая экономичная модель
        val cheapest = successfulResults
            .filter { it.estimatedCost != null }
            .minByOrNull { it.estimatedCost ?: Double.MAX_VALUE }
        if (cheapest != null) {
            terminal.println("- Самая экономичная: ${cheapest.modelInfo.name} (\$${String.format(Locale.US, "%.6f", cheapest.estimatedCost)})")
        }

        // Наибольшее количество токенов
        val mostTokens = successfulResults.maxByOrNull { it.response?.usage?.totalTokens ?: 0 }
        if (mostTokens != null) {
            terminal.println("- Наибольшее количество токенов: ${mostTokens.modelInfo.name} (${mostTokens.response?.usage?.totalTokens})")
        }

        terminal.println()
        terminal.println(bold(cyan("📈 Рекомендации:")))
        terminal.println("- Для простых задач используйте быструю модель")
        terminal.println("- Для сложных задач используйте качественную модель")

        // Предупреждение о недоступных моделях
        val failedResults = results.filter { it.response == null }
        if (failedResults.isNotEmpty()) {
            terminal.println()
            terminal.println(yellow("⚠️  ${failedResults.size} модель(ей) недоступна: ${failedResults.map { it.modelInfo.name }.joinToString(", ")}"))
        }

        terminal.println()
    }

    /**
     * Форматирует время в человекочитаемый вид.
     */
    private fun formatTime(ms: Long): String {
        return if (ms < 1000) {
            "${ms} мс"
        } else {
            "${String.format(Locale.US, "%.1f", ms / 1000.0)} сек"
        }
    }

    override fun handleCommand(input: String): Boolean {
        return when {
            input.startsWith(":models") -> {
                val value = input.removePrefix(":models").trim()
                if (value.isEmpty()) {
                    showModelsList()
                } else {
                    selectModels(value)
                }
                true
            }
            input.startsWith(":maxTokens") -> {
                val value = input.removePrefix(":maxTokens").trim()
                handleMaxTokensCommand(value)
                true
            }
            input == ":reset" -> {
                handleResetCommand()
                true
            }
            input == ":params" -> {
                showParams()
                true
            }
            else -> false
        }
    }

    private fun showModelsList() {
        terminal.println()
        terminal.println(bold(cyan("📋 Доступные модели:")))
        for ((index, model) in allModels.withIndex()) {
            val costInfo = if (model.costPer1kInputTokens != null) " — ${model.formatTariff()}" else ""
            terminal.println("  ${index + 1}. ${model.name} (${model.modelId})$costInfo")
        }
        terminal.println()
        terminal.println("Выбраны: ${if (selectedModels.size == allModels.size) "все (${allModels.size} моделей)" else "${selectedModels.size} моделей"}")
        terminal.println()
        terminal.println(gray("Для выбора подмножества используйте: :models <idx1,idx2,...>"))
        terminal.println(gray("Например: :models 1,3"))
        terminal.println()
    }

    private fun selectModels(value: String) {
        val indices = value.split(",").mapNotNull { it.trim().toIntOrNull() }.distinct()
        val invalidIndices = indices.filter { it < 1 || it > allModels.size }

        if (invalidIndices.isNotEmpty()) {
            terminal.println(red("✗ Невалидные индексы: $invalidIndices. Доступно моделей: ${allModels.size}"))
            return
        }

        if (indices.isEmpty()) {
            terminal.println(red("✗ Укажите хотя бы один индекс модели"))
            return
        }

        selectedModels = indices.map { allModels[it - 1] }
        terminal.println(green("✓ Выбраны модели: [${selectedModels.map { it.name }.joinToString(", ")}] (${selectedModels.size} моделей)"))
    }

    private fun handleMaxTokensCommand(value: String) {
        if (value.isEmpty()) {
            terminal.println(cyan("📏 Текущее значение max tokens: $maxTokens"))
            return
        }

        val newMaxTokens = value.toIntOrNull()
        if (newMaxTokens != null && newMaxTokens in 1..128000) {
            maxTokens = newMaxTokens
            terminal.println(green("✓ Max tokens установлен: $maxTokens"))
        } else {
            terminal.println(red("✗ Некорректное значение. Max tokens должно быть числом от 1 до 128000"))
        }
    }

    private fun handleResetCommand() {
        selectedModels = allModels
        maxTokens = DEFAULT_MAX_TOKENS
        terminal.println(green("✓ Все параметры сброшены к значениям по умолчанию"))
        showParams()
    }

    private fun showParams() {
        terminal.println()
        terminal.println(bold(cyan("⚙️  Текущая конфигурация:")))
        terminal.println("   Модели: [${selectedModels.map { it.name }.joinToString(", ")}] (${selectedModels.size} моделей)")
        terminal.println("   Max tokens: $maxTokens")
        terminal.println()
    }

    override fun getHelpText(): String {
        return buildString {
            appendLine("  ${bold(":models")}             — показать список доступных моделей")
            appendLine("  ${bold(":models <idx1,...>")} — выбрать модели по индексам (например, :models 1,3)")
            appendLine("  ${bold(":maxTokens <value>")} — установить max tokens (1-128000, по умолчанию $DEFAULT_MAX_TOKENS)")
            appendLine("  ${bold(":reset")}            — сбросить все параметры к значениям по умолчанию")
            appendLine("  ${bold(":params")}           — показать текущую конфигурацию")
        }
    }

    override fun getPromptHint(): String {
        return "models=${selectedModels.size}, maxTokens=$maxTokens | :models :maxTokens :reset :params"
    }
}

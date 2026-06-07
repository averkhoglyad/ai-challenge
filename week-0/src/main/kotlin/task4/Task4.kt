package io.averkhogliad.ai.challenge.week0.task4

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.HorizontalRule
import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.llm.ChatParameters
import io.averkhogliad.ai.challenge.utils.llm.ChatResponse
import io.averkhogliad.ai.challenge.utils.llm.LlmClient
import io.averkhogliad.ai.challenge.utils.llm.LlmException
import io.averkhogliad.ai.challenge.utils.sanitizeForDisplay
import io.averkhogliad.ai.challenge.week0.Task
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

/**
 * Учебная задача #4: Демонстрация влияния temperature на генерацию.
 *
 * Выполняет один и тот же запрос с разными значениями temperature и сравнивает результаты.
 * Показывает, как temperature влияет на детерминированность и креативность ответов LLM.
 *
 * ## Интерактивные команды
 *
 * - `:temp <t1,t2,t3,...>` — установить список значений temperature (через запятую)
 * - `:temp` (без аргумента) — показать текущий список значений temperature
 * - `:maxTokens <value>` — установить max tokens для всех запросов (по умолчанию 500)
 * - `:reset` — сбросить все параметры к значениям по умолчанию
 * - `:params` — показать текущую конфигурацию
 */
class Task4(
    private val config: Config,
    private val llmClient: LlmClient
) : Task {

    override val title: String = "Task 4: Влияние temperature на генерацию"

    private val terminal = Terminal()

    // Список значений temperature (mutable state, пользователь может изменить)
    private var temperatures: List<Double> = DEFAULT_TEMPERATURES

    // Фиксированный maxTokens для честного сравнения
    private var maxTokens: Int = DEFAULT_MAX_TOKENS

    /**
     * Результат выполнения запроса с определённым значением temperature.
     */
    data class TemperatureResult(
        val temperature: Double,
        val content: String,
        val usage: ChatResponse.Usage?
    )

    companion object {
        /** Значения temperature по умолчанию */
        private val DEFAULT_TEMPERATURES = listOf(0.0, 0.7, 1.2)

        /** Значение max tokens по умолчанию для честного сравнения */
        private const val DEFAULT_MAX_TOKENS = 500
        
        /** Regex для разделения текста на слова (используется при подсчёте) */
        private val WORD_SPLIT_REGEX = "\\s+".toRegex()
    }

    override fun run(prompt: String) {
        val baseUrl = config.get("api.base-url")
        val model = config.get("api.model")

        terminal.println(bold(cyan("🤖 Модель: ")) + white(model))
        terminal.println(bold(cyan("🌐 Endpoint: ")) + white(baseUrl))
        printCurrentParams()

        terminal.println()
        terminal.println(bold(cyan("🌡️ Демонстрация влияния temperature на генерацию")))
        terminal.println()
        terminal.println(bold(yellow("📝 Запрос: ")) + white(prompt))
        terminal.println(bold(yellow("🌡️ Значения temperature: ")) + white(temperatures.toString()))
        terminal.println(bold(yellow("📏 Max tokens: ")) + white(maxTokens.toString()))
        terminal.println()
        terminal.println(cyan("⏳ Отправляю ${temperatures.size} запросов параллельно..."))
        terminal.println()

        // Фаза 1: Параллельный сбор результатов
        val results = runBlocking {
            coroutineScope {
                temperatures.mapIndexed { index, temp ->
                    async {
                        val result = queryWithTemperature(prompt, temp)
                        // Потокобезопасный прогресс-индикатор
                        synchronized(terminal) {
                            if (result != null) {
                                terminal.println(green("  ✓ [${index + 1}/${temperatures.size}] temperature=$temp — готово"))
                            } else {
                                terminal.println(red("  ✗ [${index + 1}/${temperatures.size}] temperature=$temp — ошибка"))
                            }
                        }
                        result
                    }
                }.awaitAll()
            }
        }

        terminal.println()

        // Фаза 2: Последовательный вывод детальных результатов
        val successfulResults = mutableListOf<TemperatureResult>()
        for (result in results) {
            if (result != null) {
                printDetailedResult(result)
                successfulResults.add(result)
            }
        }

        // Автоматический вывод самари
        if (successfulResults.size > 1) {
            printComparison(successfulResults)
        }
    }

    /**
     * Выполняет запрос с определённым значением temperature.
     *
     * @param prompt Пользовательский промпт
     * @param temperature Значение temperature
     * @return Результат запроса или null при ошибке
     */
    private suspend fun queryWithTemperature(prompt: String, temperature: Double): TemperatureResult? {
        return try {
            val parameters = ChatParameters(
                temperature = temperature,
                maxTokens = maxTokens
            )
            val response = llmClient.chat(prompt, parameters = parameters)
            TemperatureResult(temperature, response.content, response.usage)
        } catch (e: LlmException) {
            null
        }
    }

    /**
     * Выводит детальный результат запроса с определённым значением temperature.
     */
    private fun printDetailedResult(result: TemperatureResult) {
        terminal.println(HorizontalRule(bold(cyan("🌡️ Temperature: ${result.temperature} (${describeTemperature(result.temperature)})"))))
        terminal.println()
        terminal.println(bold(green("✓ Ответ модели:")))
        terminal.println()
        terminal.println(white(result.content))
        terminal.println()

        // Статистика токенов
        terminal.println(bold(gray("📊 Статистика:")))
        terminal.println(gray("  - Токенов в промпте: ${result.usage?.promptTokens ?: "N/A"}"))
        terminal.println(gray("  - Токенов в ответе: ${result.usage?.completionTokens ?: "N/A"}"))
        terminal.println(gray("  - Всего токенов: ${result.usage?.totalTokens ?: "N/A"}"))
        terminal.println()
    }

    /**
     * Выводит итоговое сравнение результатов.
     */
    private fun printComparison(results: List<TemperatureResult>) {
        terminal.println(HorizontalRule(bold(cyan("📊 Сравнение результатов"))))
        terminal.println()

        for (result in results) {
            val wordCount = result.content.split(WORD_SPLIT_REGEX).size
            val charCount = result.content.length
            terminal.println(bold(yellow("🌡️ Temperature ${result.temperature}:")))
            terminal.println(gray("  - Длина ответа: $charCount символов, $wordCount слов"))
            terminal.println(gray("  - Токенов в ответе: ${result.usage?.completionTokens ?: "N/A"}"))
        }

        terminal.println()
        terminal.println(bold(cyan("💡 Вывод:")))
        terminal.println(gray("  - Низкие значения temperature (0.0-0.3): ответы наиболее предсказуемы и консистентны"))
        terminal.println(gray("  - Средние значения temperature (0.5-0.8): баланс между качеством и разнообразием"))
        terminal.println(gray("  - Высокие значения temperature (1.0-2.0): ответы наиболее креативны и разнообразны"))
        terminal.println()
    }

    /**
     * Генерирует описание значения temperature на основе его абсолютного значения.
     */
    private fun describeTemperature(temp: Double): String {
        return when {
            temp == 0.0 -> "максимальная детерминированность"
            temp < 0.3 -> "высокая детерминированность"
            temp < 0.7 -> "умеренная случайность"
            temp < 1.0 -> "сбалансированный режим"
            temp < 1.5 -> "повышенная креативность"
            else -> "максимальная креативность"
        }
    }

    override fun handleCommand(input: String): Boolean {
        return when {
            input.startsWith(":temp") -> {
                handleTempCommand(input.removePrefix(":temp").trim())
                true
            }
            input.startsWith(":maxTokens") -> {
                handleMaxTokensCommand(input.removePrefix(":maxTokens").trim())
                true
            }
            input == ":reset" -> {
                handleResetCommand()
                true
            }
            input == ":params" -> {
                printCurrentParams()
                true
            }
            else -> false
        }
    }

    private fun handleTempCommand(value: String) {
        if (value.isEmpty()) {
            terminal.println(bold(yellow("🌡️ Текущие значения temperature:")) + white(temperatures.toString()))
            return
        }

        val newTemps = value.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toDoubleOrNull() }

        if (newTemps.isEmpty()) {
            terminal.println(red("✗ Некорректные значения. Укажите числа через запятую."))
            return
        }

        val invalidTemps = newTemps.filter { it !in 0.0..2.0 }
        if (invalidTemps.isNotEmpty()) {
            terminal.println(red("✗ Значения вне диапазона 0.0-2.0: $invalidTemps"))
            return
        }

        temperatures = newTemps
        terminal.println(green("✓ Значения temperature установлены: $temperatures"))
    }

    private fun handleMaxTokensCommand(value: String) {
        if (value.isEmpty()) {
            terminal.println(bold(yellow("📏 Текущее значение max tokens:")) + white(maxTokens.toString()))
            return
        }

        val max = value.toIntOrNull()
        if (max == null || max !in 1..128_000) {
            terminal.println(red("✗ Некорректное значение. Max tokens должно быть числом от 1 до 128000"))
            return
        }

        maxTokens = max
        terminal.println(green("✓ Max tokens установлен: $max"))
    }

    private fun handleResetCommand() {
        temperatures = DEFAULT_TEMPERATURES
        maxTokens = DEFAULT_MAX_TOKENS
        terminal.println(green("✓ Все параметры сброшены к значениям по умолчанию"))
        printCurrentParams()
    }

    private fun printCurrentParams() {
        terminal.println()
        terminal.println(bold(yellow("⚙️  Текущая конфигурация:")))
        terminal.println(gray("   Значения temperature: $temperatures"))
        terminal.println(gray("   Max tokens: $maxTokens"))
        terminal.println()
    }

    override fun getHelpText(): String {
        return buildString {
            appendLine("  ${bold(":temp <t1,t2,...>")} — установить список значений temperature (0.0-2.0)")
            appendLine("  ${bold(":temp")}             — показать текущий список значений temperature")
            appendLine("  ${bold(":maxTokens <value>")} — установить max tokens (1-128000, по умолчанию $DEFAULT_MAX_TOKENS)")
            appendLine("  ${bold(":reset")}            — сбросить все параметры к значениям по умолчанию")
            appendLine("  ${bold(":params")}           — показать текущую конфигурацию")
        }
    }

    override fun getPromptHint(): String {
        val tempsStr = temperatures.joinToString(",")
        return "temp=[$tempsStr], maxTokens=$maxTokens | :temp :maxTokens :reset :params"
    }
}

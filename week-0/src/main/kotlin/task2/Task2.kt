package io.averkhogliad.ai.challenge.week0.task2

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.llm.ChatParameters
import io.averkhogliad.ai.challenge.utils.llm.LlmClient
import io.averkhogliad.ai.challenge.utils.sanitizeForDisplay
import io.averkhogliad.ai.challenge.week0.Task
import kotlinx.coroutines.runBlocking

/**
 * Учебная задача #2: расширенный chat-completion с интерактивным контролем параметров генерации.
 *
 * Демонстрирует использование [LlmClient] с расширенными параметрами:
 * - **temperature** — контроль случайности генерации (0.0 - 2.0)
 * - **maxTokens** — ограничение длины ответа (1 - 128 000 токенов)
 * - **stop** — стоп-последовательности для завершения генерации (максимум 4)
 *
 * ## Интерактивные команды
 *
 * Параметры настраиваются через команды в REPL:
 * - `:temp <value>` — установить температуру (0.0-2.0), например `:temp 0.7`
 * - `:maxTokens <value>` — установить max tokens (1-128000), например `:maxTokens 500`
 * - `:stop <seq1,seq2,...>` — установить стоп-последовательности (максимум 4), например `:stop END,STOP`
 * - `:reset` — сбросить все параметры к значениям по умолчанию
 * - `:params` — показать текущие параметры
 *
 * Пустое значение сбрасывает параметр: `:temp` (без значения) сбросит температуру.
 */
class Task2(
    private val config: Config,
    private val llmClient: LlmClient
) : Task {

    override val title: String = "Task 2: расширенный chat-completion с параметрами"

    private val terminal = Terminal()

    // Текущие параметры (mutable state)
    private var currentTemperature: Double? = DEFAULT_TEMPERATURE
    private var currentMaxTokens: Int? = DEFAULT_MAX_TOKENS
    private var currentStopSequences: List<String>? = null

    companion object {
        /** Значение temperature по умолчанию */
        private const val DEFAULT_TEMPERATURE = 0.7
        
        /** Значение max tokens по умолчанию */
        private const val DEFAULT_MAX_TOKENS = 500
        
        /** Максимально допустимое значение max tokens */
        private const val MAX_TOKENS_UPPER_BOUND = 128_000
        
        /** Максимальное количество stop sequences (ограничение API) */
        private const val MAX_STOP_SEQUENCES = 4
    }

    /**
     * Отправляет промпт модели с текущими параметрами генерации.
     *
     * @param prompt пользовательский промпт
     * @return текст ответа модели
     * @throws io.averkhogliad.ai.challenge.utils.llm.LlmException при ошибке API
     */
    suspend fun ask(prompt: String): String {
        val parameters = buildParameters()
        val response = llmClient.chat(prompt, parameters = parameters)
        return response.content
    }

    /**
     * Строит параметры генерации из текущего состояния.
     */
    private fun buildParameters(): ChatParameters {
        return ChatParameters(
            temperature = currentTemperature,
            maxTokens = currentMaxTokens,
            stop = currentStopSequences,
            responseFormat = null
        )
    }

    /**
     * Обрабатывает специфичные для Task2 команды настройки параметров.
     *
     * Поддерживаемые команды:
     * - `:temp <value>` — установить температуру (0.0-2.0)
     * - `:maxTokens <value>` — установить max tokens (1-128000)
     * - `:stop <seq1,seq2,...>` — установить стоп-последовательности (максимум 4)
     * - `:reset` — сбросить все параметры к значениям по умолчанию
     * - `:params` — показать текущие параметры
     *
     * @param input ввод пользователя (уже trim'нутый)
     * @return true если команда была обработана, false если это не команда Task2
     */
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
            input.startsWith(":stop") -> {
                handleStopCommand(input.removePrefix(":stop").trim())
                true
            }
            input == ":reset" -> {
                handleResetCommand()
                true
            }
            input == ":params" -> {
                printCurrentParameters()
                true
            }
            else -> false
        }
    }

    /**
     * Возвращает текст справки по специфичным командам Task2.
     */
    override fun getHelpText(): String {
        return buildString {
            appendLine("  ${bold(":temp <value>")}       — установить температуру (0.0-2.0)")
            appendLine("  ${bold(":maxTokens <value>")}  — установить max tokens (1-$MAX_TOKENS_UPPER_BOUND)")
            appendLine("  ${bold(":stop <seq,...>")}     — установить стоп-последовательности (максимум $MAX_STOP_SEQUENCES)")
            appendLine("  ${bold(":reset")}              — сбросить все параметры")
            appendLine("  ${bold(":params")}             — показать текущие параметры")
        }
    }

    /**
     * Возвращает текст приглашения с информацией о командах и текущих параметрах.
     */
    override fun getPromptHint(): String {
        val tempStr = currentTemperature?.toString() ?: "default"
        val maxStr = currentMaxTokens?.toString() ?: "default"
        val stopStr = currentStopSequences?.joinToString(",") ?: "none"
        
        return "temp=$tempStr, maxTokens=$maxStr, stop=$stopStr | :temp :maxTokens :stop :reset :params"
    }

    private fun handleTempCommand(value: String) {
        if (value.isEmpty()) {
            currentTemperature = null
            terminal.println(green("✓ Temperature сброшена (будет использоваться значение API по умолчанию)"))
        } else {
            val temp = value.toDoubleOrNull()
            if (temp != null && temp in 0.0..2.0) {
                currentTemperature = temp
                terminal.println(green("✓ Temperature установлена: $temp"))
            } else {
                terminal.println(red("✗ Некорректное значение. Temperature должна быть числом от 0.0 до 2.0"))
            }
        }
    }

    private fun handleMaxTokensCommand(value: String) {
        if (value.isEmpty()) {
            currentMaxTokens = null
            terminal.println(green("✓ Max tokens сброшен (будет использоваться значение API по умолчанию)"))
        } else {
            val max = value.toIntOrNull()
            if (max != null && max in 1..MAX_TOKENS_UPPER_BOUND) {
                currentMaxTokens = max
                terminal.println(green("✓ Max tokens установлен: $max"))
            } else {
                terminal.println(red("✗ Некорректное значение. Max tokens должно быть от 1 до $MAX_TOKENS_UPPER_BOUND"))
            }
        }
    }

    private fun handleStopCommand(value: String) {
        if (value.isEmpty()) {
            currentStopSequences = null
            terminal.println(green("✓ Stop sequences сброшены"))
        } else {
            val sequences = value.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            
            if (sequences.isEmpty()) {
                currentStopSequences = null
                terminal.println(green("✓ Stop sequences сброшены"))
            } else if (sequences.size > MAX_STOP_SEQUENCES) {
                terminal.println(red("✗ Слишком много stop sequences. Максимум $MAX_STOP_SEQUENCES, получено ${sequences.size}"))
            } else {
                currentStopSequences = sequences
                terminal.println(green("✓ Stop sequences установлены: ${sequences.joinToString(", ")}"))
            }
        }
    }

    private fun handleResetCommand() {
        currentTemperature = DEFAULT_TEMPERATURE
        currentMaxTokens = DEFAULT_MAX_TOKENS
        currentStopSequences = null
        terminal.println(green("✓ Все параметры сброшены к значениям по умолчанию"))
        printCurrentParameters()
    }

    private fun printCurrentParameters() {
        terminal.println()
        terminal.println(bold(yellow("⚙️  Текущие параметры генерации:")))
        terminal.println(gray("   Temperature: ${currentTemperature ?: "не задана (API default)"}"))
        terminal.println(gray("   Max tokens: ${currentMaxTokens ?: "не задан (API default)"}"))
        terminal.println(gray("   Stop sequences: ${currentStopSequences?.joinToString(", ") ?: "не заданы"}"))
        terminal.println()
    }

    /**
     * Точка входа задачи: отправляет промпт модели с текущими параметрами.
     *
     * Использует `runBlocking` для блокировки потока в консольном приложении,
     * так как REPL-цикл синхронный и не требует асинхронной обработки.
     */
    override fun run(prompt: String) {
        val baseUrl = config.get("api.base-url")
        val model = config.get("api.model")

        terminal.println(bold(cyan("🤖 Модель: ")) + white(model))
        terminal.println(bold(cyan("🌐 Endpoint: ")) + white(baseUrl))
        printCurrentParameters()

        runBlocking {
            try {
                terminal.println(bold(yellow("⏳ Отправляю запрос к модели...")))
                
                val answer = ask(prompt)
                
                terminal.println()
                terminal.println(bold(green("✓ Ответ модели:")))
                terminal.println()
                terminal.println(white(answer))
                
            } catch (e: Exception) {
                terminal.println()
                terminal.println(bold(red("✗ Ошибка: ")) + red(sanitizeForDisplay(e.message ?: "неизвестная ошибка")))
                if (System.getProperty("debug") != null) {
                    e.printStackTrace()
                }
            }
        }
    }
}

package io.averkhogliad.ai.challenge.week0.task3

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.HorizontalRule
import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.llm.ChatParameters
import io.averkhogliad.ai.challenge.utils.llm.LlmClient
import io.averkhogliad.ai.challenge.utils.llm.LlmException
import io.averkhogliad.ai.challenge.utils.sanitizeForDisplay
import io.averkhogliad.ai.challenge.week0.Task
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

/**
 * Учебная задача #3: Промпт-инжиниринг с модульными модификаторами.
 *
 * Демонстрирует различные техники промпт-инжиниринга:
 * - **Zero-shot** — режим direct без модификаторов
 * - **Chain-of-thought** — модификатор `:step` (пошаговое решение)
 * - **Meta-prompting** — модификатор `:meta` (генерация оптимального промпта)
 * - **Role-playing** — команда `:role` (установка роли)
 * - **Multi-persona / Debate** — режим `:experts` (группа экспертов)
 * - **Synthesis** — опция `:summary` (итоговое заключение)
 *
 * ## Архитектура
 *
 * Базовый режим (один):
 * - `direct` — один вызов API
 * - `experts` — N вызовов + опциональное summary
 *
 * Модификаторы (можно комбинировать):
 * - `:step on/off` — пошаговый режим
 * - `:meta on/off` — метапромпт
 * - `:role <name> [desc]` — роль (только в режиме direct)
 *
 * Опции режима experts:
 * - `:experts <roles>` — задать группу экспертов
 * - `:summary on/off` — финальное summary
 *
 * ## Интерактивные команды
 *
 * - `:mode direct|experts` — переключить базовый режим
 * - `:step [on|off]` — переключить пошаговый режим (без аргумента — toggle)
 * - `:meta [on|off]` — переключить метапромпт (без аргумента — toggle)
 * - `:role <name> [desc]` — установить роль (только direct)
 * - `:role` — сбросить роль
 * - `:experts <r1,r2,...>` — задать группу экспертов
 * - `:summary [on|off]` — переключить финальное summary (без аргумента — toggle)
 * - `:config` — показать текущую конфигурацию
 * - `:reset` — сбросить всё к дефолту
 */
class Task3(
    private val config: Config,
    private val llmClient: LlmClient
) : Task {

    override val title: String = "Task 3: Промпт-инжиниринг с модульными модификаторами"

    private val terminal = Terminal()

    // Конфигурация (mutable state)
    private var mode: Mode = Mode.DIRECT
    private var stepByStep: Boolean = false
    private var metaPrompt: Boolean = false
    private var role: RoleConfig? = null
    private var experts: List<ExpertConfig> = DEFAULT_EXPERTS
    private var summaryEnabled: Boolean = false

    // Вложенные типы
    enum class Mode { DIRECT, EXPERTS }
    data class RoleConfig(val name: String, val description: String)
    data class ExpertConfig(val name: String, val systemPrompt: String)

    companion object {
        val DEFAULT_EXPERTS = listOf(
            ExpertConfig("Аналитик", "Ты — аналитик. Анализируй задачу системно, выявляй закономерности и структуры."),
            ExpertConfig("Инженер", "Ты — инженер. Предлагай практические, реализуемые решения с технической точки зрения."),
            ExpertConfig("Критик", "Ты — критик. Находи слабые места, риски и потенциальные проблемы в решениях.")
        )
    }

    /**
     * Точка входа задачи: отправляет промпт модели с учётом текущей конфигурации.
     *
     * Использует `runBlocking` для блокировки потока в консольном приложении,
     * так как REPL-цикл синхронный и не требует асинхронной обработки.
     */
    override fun run(prompt: String) {
        val baseUrl = config.get("api.base-url")
        val model = config.get("api.model")

        terminal.println(bold(cyan("🤖 Модель: ")) + white(model))
        terminal.println(bold(cyan("🌐 Endpoint: ")) + white(baseUrl))
        printCurrentConfig()

        runBlocking {
            try {
                when (mode) {
                    Mode.DIRECT -> runDirect(prompt)
                    Mode.EXPERTS -> runExperts(prompt)
                }
            } catch (e: Exception) {
                terminal.println()
                terminal.println(bold(red("✗ Ошибка: ")) + red(sanitizeForDisplay(e.message ?: "неизвестная ошибка")))
                if (System.getProperty("debug") != null) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Режим direct: один вызов API с опциональными модификаторами.
     */
    private suspend fun runDirect(prompt: String) {
        var effectivePrompt = prompt
        
        // Применяем мета-промпт, если включён
        if (metaPrompt) {
            effectivePrompt = applyMetaPrompt(prompt)
        }
        
        // Строим system prompt из роли и пошагового режима
        val systemPrompt = buildSystemPrompt()
        
        // Формируем информативное сообщение о активных модификаторах
        val modifiers = buildList {
            role?.let { add("role:${it.name}") }
            if (stepByStep) add("step")
            if (metaPrompt) add("meta")
        }.joinToString(" + ")
        
        val statusMessage = if (modifiers.isNotEmpty()) {
            "⏳ [$modifiers] Генерирую ответ..."
        } else {
            "⏳ Генерирую ответ..."
        }
        terminal.println(bold(yellow(statusMessage)))
        
        try {
            val response = llmClient.chat(
                effectivePrompt,
                systemPrompt = systemPrompt,
                parameters = ChatParameters.DEFAULT
            )
            
            terminal.println()
            terminal.println(bold(green("✓ Ответ модели:")))
            terminal.println()
            terminal.println(white(response.content))
        } catch (e: LlmException) {
            terminal.println()
            terminal.println(red("✗ Ошибка при запросе: ${sanitizeForDisplay(e.message ?: "неизвестная ошибка")}"))
        }
    }

    /**
     * Режим experts: несколько вызовов API с разными system prompts.
     *
     * Мета-промпт применяется один раз до цикла экспертов, так как он генерирует
     * оптимальный user prompt для задачи, а не для конкретного эксперта.
     * Каждый эксперт получает один и тот же промпт, но с разной ролью (system prompt).
     */
    private suspend fun runExperts(prompt: String) {
        // Применяем мета-промпт один раз до цикла экспертов
        var effectivePrompt = prompt
        if (metaPrompt) {
            effectivePrompt = applyMetaPrompt(prompt)
        }
        
        terminal.println(cyan("⏳ Опрашиваю ${experts.size} экспертов параллельно..."))
        terminal.println()
        
        // Фаза 1: Параллельный сбор ответов экспертов
        val expertResults = coroutineScope {
            experts.mapIndexed { index, expert ->
                async {
                    val result = queryExpert(effectivePrompt, expert)
                    // Потокобезопасный прогресс-индикатор
                    synchronized(terminal) {
                        if (result != null) {
                            terminal.println(green("  ✓ [${index + 1}/${experts.size}] ${expert.name} — готово"))
                        } else {
                            terminal.println(red("  ✗ [${index + 1}/${experts.size}] ${expert.name} — ошибка"))
                        }
                    }
                    expert.name to result
                }
            }.awaitAll()
        }
        
        terminal.println()
        
        // Фаза 2: Последовательный вывод ответов экспертов
        val responses = mutableListOf<Pair<String, String>>()
        for ((expertName, content) in expertResults) {
            if (content != null) {
                responses.add(expertName to content)
                printExpertResponse(expertName, content)
            }
        }
        
        // Генерируем summary, если включён и есть несколько ответов (для одного эксперта summary избыточен)
        if (summaryEnabled && responses.size > 1) {
            terminal.println(bold(yellow("⏳ [summary] Синтезирую итоговое заключение...")))
            val summary = generateSummary(responses)
            if (summary != null) {
                terminal.println()
                terminal.println(HorizontalRule(bold(cyan("📊 Итоговое заключение"))))
                terminal.println()
                terminal.println(summary)
                terminal.println()
                terminal.println(HorizontalRule())
                terminal.println()
            }
        } else if (summaryEnabled && responses.size == 1) {
            terminal.println(gray("ℹ️ Summary пропущен: только один эксперт, синтез не требуется"))
        }
    }
    
    /**
     * Выполняет запрос к одному эксперту.
     *
     * @param prompt Пользовательский промпт
     * @param expert Конфигурация эксперта (имя и system prompt)
     * @return Ответ эксперта или null при ошибке
     */
    private suspend fun queryExpert(prompt: String, expert: ExpertConfig): String? {
        return try {
            val systemPrompt = expert.systemPrompt + if (stepByStep) "\nРешай пошагово." else ""
            val response = llmClient.chat(
                prompt,
                systemPrompt = systemPrompt,
                parameters = ChatParameters.DEFAULT
            )
            response.content
        } catch (e: LlmException) {
            null
        }
    }
    
    /**
     * Выводит ответ эксперта с заголовком и разделителями.
     */
    private fun printExpertResponse(expertName: String, content: String) {
        terminal.println()
        terminal.println(HorizontalRule(bold(cyan("🔍 $expertName"))))
        terminal.println()
        terminal.println(content)
        terminal.println()
        terminal.println(HorizontalRule())
        terminal.println()
    }

    /**
     * Применяет мета-промпт: просит модель составить оптимальный промпт для задачи.
     *
     * @param prompt исходный промпт
     * @return сгенерированный промпт или исходный при ошибке
     */
    private suspend fun applyMetaPrompt(prompt: String): String {
        terminal.println(bold(yellow("⏳ [meta] Шаг 1: Составляю оптимальный промпт...")))
        
        val metaSystemPrompt = """
            Ты — эксперт по промпт-инжинирингу. 
            Составь оптимальный промпт для решения следующей задачи.
            Верни ТОЛЬКО текст промпта, без пояснений.
        """.trimIndent()
        
        val generatedPrompt = try {
            llmClient.chat(
                prompt,
                systemPrompt = metaSystemPrompt,
                parameters = ChatParameters.DEFAULT
            ).content
        } catch (e: LlmException) {
            terminal.println(red("✗ Ошибка при генерации мета-промпта: ${sanitizeForDisplay(e.message ?: "неизвестная ошибка")}"))
            terminal.println(gray("Использую исходный промпт."))
            return prompt
        }
        
        // Показываем сгенерированный промпт
        terminal.println()
        terminal.println(HorizontalRule(bold(cyan("📋 Сгенерированный промпт"))))
        terminal.println()
        terminal.println(generatedPrompt)
        terminal.println()
        terminal.println(HorizontalRule())
        terminal.println()
        terminal.println(bold(yellow("⏳ [meta] Шаг 2: Решаю задачу по промпту...")))
        
        return generatedPrompt
    }

    /**
     * Строит system prompt из текущей конфигурации (роль + пошаговый режим).
     *
     * @return system prompt или null, если модификаторы не заданы
     */
    private fun buildSystemPrompt(): String? {
        val parts = mutableListOf<String>()
        
        role?.let {
            parts.add(it.description)
        }
        
        if (stepByStep) {
            parts.add("Решай задачу пошагово, объясняя каждый шаг.")
        }
        
        return if (parts.isEmpty()) null else parts.joinToString("\n\n")
    }

    /**
     * Генерирует итоговое заключение на основе ответов экспертов.
     *
     * @param responses список пар (имя эксперта, ответ)
     * @return итоговое заключение или null при ошибке
     */
    private suspend fun generateSummary(responses: List<Pair<String, String>>): String? {
        val allResponses = responses.joinToString("\n\n") { (name, text) ->
            "=== $name ===\n$text"
        }
        
        val summaryPrompt = """
            На основе мнений экспертов ниже, составь итоговое заключение.
            Выдели ключевые точки согласия и разногласия, дай рекомендацию.
            
            $allResponses
        """.trimIndent()
        
        return try {
            llmClient.chat(
                summaryPrompt,
                systemPrompt = "Ты — модератор дискуссии. Синтезируй мнения экспертов в единое заключение.",
                parameters = ChatParameters.DEFAULT
            ).content
        } catch (e: LlmException) {
            terminal.println(red("✗ Ошибка при генерации summary: ${sanitizeForDisplay(e.message ?: "неизвестная ошибка")}"))
            null
        }
    }

    /**
     * Обрабатывает специфичные для Task3 команды.
     *
     * @param input ввод пользователя (уже trim'нутый)
     * @return true если команда была обработана, false если это не команда Task3
     */
    override fun handleCommand(input: String): Boolean {
        return when {
            input.startsWith(":mode") -> {
                handleModeCommand(input.removePrefix(":mode").trim())
                true
            }
            input.startsWith(":step") -> {
                handleStepCommand(input.removePrefix(":step").trim())
                true
            }
            input.startsWith(":meta") -> {
                handleMetaCommand(input.removePrefix(":meta").trim())
                true
            }
            input.startsWith(":role") -> {
                handleRoleCommand(input.removePrefix(":role").trim())
                true
            }
            input.startsWith(":experts") -> {
                handleExpertsCommand(input.removePrefix(":experts").trim())
                true
            }
            input.startsWith(":summary") -> {
                handleSummaryCommand(input.removePrefix(":summary").trim())
                true
            }
            input == ":config" -> {
                printCurrentConfig()
                true
            }
            input == ":reset" -> {
                handleResetCommand()
                true
            }
            else -> false
        }
    }

    private fun handleModeCommand(value: String) {
        when (value.lowercase()) {
            "direct" -> {
                mode = Mode.DIRECT
                terminal.println(green("✓ Режим: direct (один вызов API)"))
            }
            "experts" -> {
                mode = Mode.EXPERTS
                terminal.println(green("✓ Режим: experts (группа экспертов)"))
            }
            else -> {
                terminal.println(red("✗ Некорректный режим. Используйте: direct или experts"))
            }
        }
    }

    private fun handleStepCommand(value: String) {
        when (value.lowercase()) {
            "" -> {
                stepByStep = !stepByStep
                val state = if (stepByStep) "включён" else "выключен"
                terminal.println(green("✓ Пошаговый режим $state"))
            }
            "on" -> {
                stepByStep = true
                terminal.println(green("✓ Пошаговый режим включён"))
            }
            "off" -> {
                stepByStep = false
                terminal.println(green("✓ Пошаговый режим выключен"))
            }
            else -> {
                terminal.println(red("✗ Некорректное значение. Используйте: on или off"))
            }
        }
    }

    private fun handleMetaCommand(value: String) {
        when (value.lowercase()) {
            "" -> {
                metaPrompt = !metaPrompt
                val state = if (metaPrompt) "включён" else "выключен"
                terminal.println(green("✓ Метапромпт $state"))
            }
            "on" -> {
                metaPrompt = true
                terminal.println(green("✓ Метапромпт включён"))
            }
            "off" -> {
                metaPrompt = false
                terminal.println(green("✓ Метапромпт выключен"))
            }
            else -> {
                terminal.println(red("✗ Некорректное значение. Используйте: on или off"))
            }
        }
    }

    private fun handleRoleCommand(value: String) {
        if (mode == Mode.EXPERTS) {
            terminal.println(yellow("⚠ Роль игнорируется в режиме experts. Используйте :mode direct."))
            return
        }
        
        if (value.isEmpty()) {
            role = null
            terminal.println(green("✓ Роль сброшена"))
        } else {
            val parts = value.split(" ", limit = 2)
            val roleName = parts[0]
            val roleDesc = if (parts.size > 1) parts[1] else "Ты — $roleName."
            role = RoleConfig(roleName, roleDesc)
            terminal.println(green("✓ Роль установлена: $roleName"))
        }
    }

    private fun handleExpertsCommand(value: String) {
        if (value.isEmpty()) {
            terminal.println(red("✗ Укажите роли экспертов через запятую"))
            return
        }
        
        val roles = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (roles.isEmpty()) {
            terminal.println(red("✗ Укажите хотя бы одну роль эксперта"))
            return
        }
        
        experts = roles.map { roleName ->
            ExpertConfig(
                name = roleName.replaceFirstChar { it.uppercase() },
                systemPrompt = "Ты — $roleName. Отвечай с позиции эксперта в этой области."
            )
        }
        terminal.println(green("✓ Эксперты: ${experts.joinToString(", ") { it.name }}"))
    }

    private fun handleSummaryCommand(value: String) {
        when (value.lowercase()) {
            "" -> {
                summaryEnabled = !summaryEnabled
                val state = if (summaryEnabled) "включён" else "выключен"
                terminal.println(green("✓ Summary $state"))
            }
            "on" -> {
                summaryEnabled = true
                terminal.println(green("✓ Summary включён"))
            }
            "off" -> {
                summaryEnabled = false
                terminal.println(green("✓ Summary выключен"))
            }
            else -> {
                terminal.println(red("✗ Некорректное значение. Используйте: on или off"))
            }
        }
    }

    private fun handleResetCommand() {
        mode = Mode.DIRECT
        stepByStep = false
        metaPrompt = false
        role = null
        experts = DEFAULT_EXPERTS
        summaryEnabled = false
        terminal.println(green("✓ Все параметры сброшены к значениям по умолчанию"))
        printCurrentConfig()
    }

    private fun printCurrentConfig() {
        terminal.println()
        terminal.println(bold(yellow("⚙️  Текущая конфигурация:")))
        terminal.println(gray("   Режим: ${mode.name.lowercase()}"))
        terminal.println(gray("   Пошаговый режим: ${if (stepByStep) "включён" else "выключен"}"))
        terminal.println(gray("   Метапромпт: ${if (metaPrompt) "включён" else "выключен"}"))
        terminal.println(gray("   Роль: ${role?.name ?: "не задана"}"))
        if (mode == Mode.EXPERTS) {
            terminal.println(gray("   Эксперты: ${experts.joinToString(", ") { it.name }}"))
            terminal.println(gray("   Summary: ${if (summaryEnabled) "включён" else "выключен"}"))
        }
        terminal.println()
    }

    /**
     * Возвращает текст справки по специфичным командам Task3.
     */
    override fun getHelpText(): String {
        return buildString {
            appendLine("  ${bold(":mode direct|experts")} — переключить базовый режим")
            appendLine("  ${bold(":step [on|off]")}      — переключить пошаговый режим (без аргумента — toggle)")
            appendLine("  ${bold(":meta [on|off]")}      — переключить метапромпт (без аргумента — toggle)")
            appendLine("  ${bold(":role <name> [desc]")} — установить роль (только direct)")
            appendLine("  ${bold(":role")}               — сбросить роль")
            appendLine("  ${bold(":experts <roles>")}    — задать группу экспертов (через запятую)")
            appendLine("  ${bold(":summary [on|off]")}   — переключить финальное summary (без аргумента — toggle, только experts)")
            appendLine("  ${bold(":config")}             — показать текущую конфигурацию")
            appendLine("  ${bold(":reset")}              — сбросить все параметры")
        }
    }

    /**
     * Возвращает текст приглашения с информацией о текущей конфигурации.
     */
    override fun getPromptHint(): String {
        val modeStr = mode.name.lowercase()
        val modifiers = buildList {
            if (stepByStep) add("step")
            if (metaPrompt) add("meta")
            role?.let { add("role:${it.name}") }
        }.joinToString(", ")
        
        return if (modifiers.isEmpty()) {
            "mode=$modeStr | :mode :step :meta :role :experts :summary :config :reset"
        } else {
            "mode=$modeStr, $modifiers | :mode :step :meta :role :experts :summary :config :reset"
        }
    }
}

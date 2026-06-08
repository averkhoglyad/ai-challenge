package io.averkhogliad.ai.challenge.week0.domain.service

import io.averkhogliad.ai.challenge.week0.domain.Prompt
import io.averkhogliad.ai.challenge.week0.domain.TaskResult
import io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Domain-сервис для промпт-инжиниринга.
 *
 * - Построение промптов с модификаторами (step-by-step, role, meta-prompt)
 * - Параллельный опрос группы экспертов
 * - Синтез итогового заключения (summary)
 *
 * Зависит только от [LlmPort] (port) и domain-моделей ([Prompt], [TaskResult], [TaskExecutionConfig]).
 * Не зависит от UI (terminal) и конкретных LLM-клиентов.
 *
 * ## Режимы
 * - [Mode.DIRECT] — один вызов API с опциональными модификаторами
 * - [Mode.EXPERTS] — N параллельных вызовов с разными system prompts + опциональное summary
 *
 * ## Модификаторы
 * - [step] — добавляет инструкцию «решай пошагово» в system prompt
 * - [role] — устанавливает роль/персону для модели
 * - [metaPrompt] — генерирует оптимальный промпт перед основным запросом
 */
class PromptEngineeringService(
    private val llmPort: LlmPort
) {
    enum class Mode { DIRECT, EXPERTS }

    /**
     * Domain-представление ответа одного эксперта.
     *
     * @property name имя эксперта (роль)
     * @property result результат выполнения запроса от имени эксперта
     */
    data class ExpertResponse(
        val name: String,
        val result: TaskResult
    )

    /**
     * Результат выполнения промпт-инжиниринга.
     *
     * Содержит все возможные выходные данные в зависимости от режима:
     * - [Mode.DIRECT]: заполнен [directResult], возможно [metaGeneratedPrompt]
     * - [Mode.EXPERTS]: заполнены [expertResponses] и опционально [summary]
     *
     * @property mode режим, в котором выполнялся запрос
     * @property directResult результат в режиме DIRECT (null для EXPERTS)
     * @property expertResponses ответы экспертов (пустой список для DIRECT)
     * @property summary итоговое заключение (null если не запрашивалось)
     * @property metaGeneratedPrompt сгенерированный мета-промпт (null если metaPrompt=false)
     */
    data class ExecuteResult(
        val mode: Mode,
        val directResult: TaskResult? = null,
        val expertResponses: List<ExpertResponse> = emptyList(),
        val summary: TaskResult? = null,
        val metaGeneratedPrompt: Prompt? = null
    )

    companion object {
        /**
         * Эксперты по умолчанию.
         * Каждый эксперт получает уникальный system prompt, определяющий его роль.
         */
        val DEFAULT_EXPERTS = listOf("Аналитик", "Инженер", "Критик")

        /** System prompt для мета-промпта (генерация оптимального промпта). */
        private val META_SYSTEM_PROMPT = """
            Ты — эксперт по промпт-инжинирингу. 
            Составь оптимальный промпт для решения следующей задачи.
            Верни ТОЛЬКО текст промпта, без пояснений.
        """.trimIndent()

        /** System prompt для синтеза мнений экспертов. */
        private val SUMMARY_SYSTEM_PROMPT =
            "Ты — модератор дискуссии. Синтезируй мнения экспертов в единое заключение."

        /** Шаблон system prompt для эксперта. */
        private fun expertSystemPrompt(role: String) =
            "Ты — $role. Отвечай с позиции эксперта в этой области."
    }

    /**
     * Выполняет промпт-инжиниринг в соответствии с заданным режимом и модификаторами.
     *
     * @param prompt исходный промпт пользователя
     * @param mode режим: DIRECT (один вызов) или EXPERTS (группа экспертов)
     * @param step инструкция для пошагового режима (null — без пошагового режима)
     * @param meta флаг мета-анализа (true — генерировать оптимальный промпт перед основным запросом)
     * @param role роль модели (null — без роли). В режиме EXPERTS игнорируется.
     * @param experts список имён экспертов (только для EXPERTS)
     * @param summary флаг необходимости синтеза итогового заключения (только для EXPERTS)
     * @param config конфигурация выполнения (temperature, maxTokens, modelId)
     * @return [ExecuteResult] с результатами выполнения
     */
    suspend fun execute(
        prompt: Prompt,
        mode: Mode,
        step: String? = null,
        meta: Boolean = false,
        role: String? = null,
        experts: List<String> = DEFAULT_EXPERTS,
        summary: Boolean = false,
        config: TaskExecutionConfig
    ): ExecuteResult {
        // Применяем мета-промпт, если включён
        val effectivePrompt = if (meta) {
            applyMetaPrompt(prompt, config)
        } else {
            prompt
        }

        return when (mode) {
            Mode.DIRECT -> executeDirect(effectivePrompt, step, role, config)
            Mode.EXPERTS -> executeExperts(effectivePrompt, step, experts, summary, config)
        }
    }

    /**
     * Применяет мета-промпт: просит модель составить оптимальный промпт для задачи.
     *
     * @param prompt исходный промпт
     * @param config конфигурация выполнения
     * @return сгенерированный промпт или исходный при ошибке
     */
    private suspend fun applyMetaPrompt(prompt: Prompt, config: TaskExecutionConfig): Prompt {
        return try {
            val result = llmPort.chatWithMessages(
                listOf(
                    ChatMessage.system(META_SYSTEM_PROMPT),
                    ChatMessage.user(prompt.value)
                ),
                config
            )
            if (result is TaskResult.Success) {
                Prompt(result.content.trim())
            } else {
                prompt
            }
        } catch (e: Exception) {
            // Ошибка мета-промпта — возвращаем исходный промпт
            // Логирование должно выполняться на уровне приложения/инфраструктуры
            prompt
        }
    }

    /**
     * Режим DIRECT: один вызов API с опциональными модификаторами.
     *
     * Алгоритм:
     * 1. Если задан [role], строим system prompt с описанием роли
     * 2. Если задан [step], добавляем инструкцию пошагового решения
     * 3. Отправляем запрос через [LlmPort.chatWithMessages] (или [LlmPort.chat])
     */
    private suspend fun executeDirect(
        prompt: Prompt,
        step: String?,
        role: String?,
        config: TaskExecutionConfig
    ): ExecuteResult {
        val messages = buildList {
            val systemPrompt = buildSystemPrompt(role, step)
            if (systemPrompt != null) {
                add(ChatMessage.system(systemPrompt))
            }
            add(ChatMessage.user(prompt.value))
        }

        val result = if (messages.size > 1) {
            llmPort.chatWithMessages(messages, config)
        } else {
            llmPort.chat(prompt, config)
        }

        return ExecuteResult(
            mode = Mode.DIRECT,
            directResult = result
        )
    }

    /**
     * Режим EXPERTS: параллельный опрос группы экспертов + опциональное summary.
     *
     * Алгоритм:
     * 1. Параллельно опрашиваем всех экспертов (каждый со своим system prompt)
     * 2. Если [summary]=true и экспертов > 1, генерируем итоговое заключение
     * 3. Если [summary]=true и эксперт один, summary не генерируется (избыточно)
     */
    private suspend fun executeExperts(
        prompt: Prompt,
        step: String?,
        experts: List<String>,
        summary: Boolean,
        config: TaskExecutionConfig
    ): ExecuteResult {
        // Фаза 1: Параллельный опрос экспертов
        val expertResults = coroutineScope {
            experts.map { expertName ->
                async {
                    val result = queryExpert(prompt, expertName, step, config)
                    ExpertResponse(expertName, result)
                }
            }.awaitAll()
        }

        val successfulResponses = expertResults.filter { it.result is TaskResult.Success }

        // Фаза 2: Синтез итогового заключения (только если >1 эксперта)
        val summaryResult = if (summary && successfulResponses.size > 1) {
            generateSummary(successfulResponses, config)
        } else {
            null
        }

        return ExecuteResult(
            mode = Mode.EXPERTS,
            expertResponses = expertResults,
            summary = summaryResult
        )
    }

    /**
     * Выполняет запрос к одному эксперту.
     *
     * @param prompt промпт пользователя
     * @param expertName имя эксперта (роль)
     * @param step инструкция пошагового решения (null если не нужна)
     * @param config конфигурация выполнения
     * @return [TaskResult] — успех или ошибка
     */
    private suspend fun queryExpert(
        prompt: Prompt,
        expertName: String,
        step: String?,
        config: TaskExecutionConfig
    ): TaskResult {
        val systemPrompt = buildString {
            append(expertSystemPrompt(expertName))
            if (step != null) {
                append("\n").append(step)
            }
        }

        return llmPort.chatWithMessages(
            listOf(
                ChatMessage.system(systemPrompt),
                ChatMessage.user(prompt.value)
            ),
            config
        )
    }

    /**
     * Генерирует итоговое заключение на основе ответов экспертов.
     *
     * @param responses список успешных ответов экспертов
     * @param config конфигурация выполнения
     * @return [TaskResult] — синтезированное заключение или ошибка
     */
    private suspend fun generateSummary(
        responses: List<ExpertResponse>,
        config: TaskExecutionConfig
    ): TaskResult {
        val allResponses = responses.joinToString("\n\n") { response ->
            val result = response.result
            val content = if (result is TaskResult.Success) result.content else ""
            "=== ${response.name} ===\n$content"
        }

        val summaryPrompt = """
            На основе мнений экспертов ниже, составь итоговое заключение.
            Выдели ключевые точки согласия и разногласия, дай рекомендацию.
            
            $allResponses
        """.trimIndent()

        return llmPort.chatWithMessages(
            listOf(
                ChatMessage.system(SUMMARY_SYSTEM_PROMPT),
                ChatMessage.user(summaryPrompt)
            ),
            config
        )
    }

    /**
     * Строит system prompt из роли и инструкции пошагового решения.
     *
     * Чистая функция: не имеет побочных эффектов, зависит только от входных параметров.
     *
     * @param role описание роли (null — без роли)
     * @param step инструкция пошагового решения (null — без инструкции)
     * @return system prompt или null, если модификаторы не заданы
     */
    private fun buildSystemPrompt(role: String?, step: String?): String? {
        val parts = mutableListOf<String>()

        if (role != null) {
            parts.add(role)
        }
        if (step != null) {
            parts.add(step)
        }

        return if (parts.isEmpty()) null else parts.joinToString("\n\n")
    }
}

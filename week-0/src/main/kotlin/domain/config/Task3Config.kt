package io.averkhogliad.ai.challenge.week0.domain.config

import io.averkhogliad.ai.challenge.week0.domain.config.Task3Config.Companion.DEFAULT_STEP_INSTRUCTION


/**
 * Режим промпт-инжиниринга для Task3.
 *
 * - [DIRECT] — один вызов LLM с опциональными модификаторами
 * - [EXPERTS] — параллельный опрос группы экспертов
 */
enum class Task3Mode {
    /** Одиночный вызов LLM (zero-shot, chain-of-thought, role-playing). */
    DIRECT,

    /** Параллельный опрос группы экспертов (multi-persona). */
    EXPERTS
}

/**
 * Immutable конфигурация Task3 (промпт-инжиниринг).
 *
 * Содержит параметры, специфичные для Task3:
 * режим (DIRECT/EXPERTS), step-by-step инструкцию,
 * роль модели, список экспертов, флаг суммаризации.
 *
 * В отличие от предыдущего подхода (параметры в конструкторе Task3Executor),
 * [Task3Config] хранится внутри [TaskExecutionConfig] и передаётся
 * через [TaskExecutor.execute], что позволяет CLI-слою изменять
 * настройки Task3 без пересоздания executor'а.
 *
 * @property mode Режим: [Task3Mode.DIRECT] или [Task3Mode.EXPERTS]
 * @property stepEnabled Включён ли пошаговый режим
 * @property stepInstruction Текст инструкции (null — использовать стандартную [DEFAULT_STEP_INSTRUCTION])
 * @property metaEnabled Включён ли мета-анализ (промпт для meta жёстко задан в PromptEngineeringService)
 * @property role Роль для LLM (null — без роли)
 * @property experts Список имён экспертов (только для EXPERTS)
 * @property summary Флаг суммаризации
 */
data class Task3Config(
    val mode: Task3Mode = Task3Mode.DIRECT,
    val stepEnabled: Boolean = false,
    val stepInstruction: String? = null,
    val metaEnabled: Boolean = false,
    val role: String? = null,
    val experts: List<String> = listOf(
        "Аналитик",
        "Инженер",
        "Критик"
    ),
    val summary: Boolean = false
) {
    /** Является ли режим DIRECT. */
    val isDirectMode: Boolean get() = mode == Task3Mode.DIRECT

    /** Является ли режим EXPERTS. */
    val isExpertsMode: Boolean get() = mode == Task3Mode.EXPERTS

    /** Включена ли суммаризация. */
    val isSummaryEnabled: Boolean get() = summary

    /**
     * Эффективная инструкция пошагового решения (null если [stepEnabled] = false).
     * Если [stepEnabled] = true, но [stepInstruction] = null, возвращает [DEFAULT_STEP_INSTRUCTION].
     *
     * Текст инструкции напрямую добавляется в system prompt через [PromptEngineeringService].
     */
    val effectiveStepInstruction: String?
        get() =
            if (stepEnabled) (stepInstruction ?: DEFAULT_STEP_INSTRUCTION) else null

    /** Реальная роль (null если не задана). */
    val effectiveRole: String? get() = if (role.isNullOrBlank()) null else role

    companion object {
        /** Инструкция пошагового решения по умолчанию. */
        const val DEFAULT_STEP_INSTRUCTION = "Решай задачу пошагово, объясняя каждый шаг."
    }
}

package io.averkhogliad.ai.challenge.week4.cli.domain.model

import java.time.Instant

/**
 * Состояние памяти задачи в рамках чат-сессии.
 *
 * ## Архитектурная роль
 * - **Value Object** — неизменяемый снимок извлечённых знаний о задаче
 *
 * Аккумулирует цель (goal), определённые термины, ограничения и уточнённые факты,
 * извлечённые LLM из диалога. Используется для обогащения промптов при последующих
 * запросах, чтобы модель «помнила» контекст задачи.
 *
 * ## Свойства
 * - [goal] — текущая цель задачи (опционально)
 * - [definedTerms] — список терминов в формате "имя" к "определение"
 * - [constraints] — список ограничений, сформулированных пользователем или выведенных LLM
 * - [clarifiedFacts] — список уточнённых фактов из диалога
 * - [lastUpdated] — время последнего обновления состояния
 */
data class TaskState(
    val goal: String? = null,
    val definedTerms: List<Pair<String, String>> = emptyList(),
    val constraints: List<String> = emptyList(),
    val clarifiedFacts: List<String> = emptyList(),
    val lastUpdated: Instant = Instant.now()
) {
    companion object {
        /** Пустое начальное состояние задачи. */
        val EMPTY: TaskState = TaskState()
    }
}

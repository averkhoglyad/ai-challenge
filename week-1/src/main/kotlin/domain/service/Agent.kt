package io.averkhogliad.ai.challenge.week1.domain.service

import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId

/**
 * Domain service для обработки пользовательских запросов через LLM.
 *
 * Агент инкапсулирует логику:
 * - Формирование запроса к LLM (с system prompt, контекстом и т.д.)
 * - Вызов LLM через [LlmPort]
 * - Обработка и трансформация ответа
 *
 * ## Архитектурная роль
 * - **Domain Layer** — содержит бизнес-логику, не зависит от UI/infrastructure
 * - **Зависит только** от [LlmPort] (domain port)
 * - **Может быть переиспользован** в разных executor'ах
 *
 * ## Расширяемость
 * Интерфейс позволяет создавать различные типы агентов:
 * - [SimpleAgent] — простой запрос-ответ
 * - ConversationalAgent — с памятью диалога
 * - ToolAgent — с использованием инструментов
 */
interface Agent {
    /**
     * Обрабатывает пользовательский запрос и возвращает результат.
     *
     * @param prompt пользовательский промпт
     * @param config конфигурация выполнения (temperature, maxTokens, modelId и др.)
     * @param dialogId опциональный идентификатор диалога (используется ConversationalAgent)
     * @return результат выполнения: [TaskResult.Success], [TaskResult.Error] или [TaskResult.Partial]
     */
    suspend fun process(prompt: Prompt, config: TaskExecutionConfig, dialogId: DialogId? = null): TaskResult
}

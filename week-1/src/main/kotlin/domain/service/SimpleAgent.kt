package io.averkhogliad.ai.challenge.week1.domain.service

import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig

/**
 * Простой агент для одиночного запроса к LLM.
 *
 * ## Логика работы
 * 1. Если задан [systemPrompt], формирует список сообщений [system, user]
 * 2. Иначе отправляет одиночный промпт через [LlmPort.chat]
 * 3. Возвращает результат как есть (без пост-обработки)
 *
 * ## Архитектурные решения
 * - **Инкапсуляция логики** — формирование запроса внутри агента, не в executor
 * - **Опциональный system prompt** — позволяет задавать контекст/инструкции модели
 * - **Делегирование** — использует [LlmPort] для реального вызова LLM
 * - **Обработка ошибок** — исключения преобразуются в [TaskResult.Error]
 *
 * @property llmPort порт для взаимодействия с LLM
 * @property systemPrompt опциональный system prompt для установки контекста
 */
class SimpleAgent(
    private val llmPort: LlmPort,
    private val systemPrompt: String? = null
) : Agent {

    override suspend fun process(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        // LlmPort (реализуемый LlmAdapter) уже обрабатывает все исключения
        // и возвращает TaskResult.Error, поэтому дополнительный try-catch не нужен
        return if (systemPrompt != null) {
            // Формируем список сообщений: system + user
            val messages = listOf(
                ChatMessage.system(systemPrompt),
                ChatMessage.user(prompt.value)
            )
            llmPort.chatWithMessages(messages, config)
        } else {
            // Одиночный промпт
            llmPort.chat(prompt, config)
        }
    }
}

package io.averkhogliad.ai.challenge.week4.cli.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskState
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStateDelta

/**
 * Порт для извлечения дельты [TaskStateDelta] из новых сообщений пользователя
 * с помощью LLM.
 *
 * ## Архитектурная роль
 * - **Domain Port** — контракт, реализуемый в infrastructure-слое
 * - **Inversion of Control** — domain определяет интерфейс, infrastructure реализует
 *
 * Реализация должна анализировать сообщение пользователя в контексте
 * текущего состояния задачи и истории диалога, чтобы определить,
 * какие изменения произошли в формулировке задачи.
 */
interface TaskStateExtractor {

    /**
     * Извлекает дельту изменений состояния задачи из сообщения пользователя.
     *
     * @param currentState текущее состояние памяти задачи
     * @param newMessages новые сообщения для анализа (обычно последнее сообщение пользователя)
     * @return [Result] c [TaskStateDelta] или ошибкой
     */
    suspend fun extract(
        currentState: TaskState,
        newMessages: List<ChatMessage>
    ): Result<TaskStateDelta>
}

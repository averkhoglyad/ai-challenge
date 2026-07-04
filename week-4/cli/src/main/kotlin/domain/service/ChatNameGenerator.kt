package io.averkhogliad.ai.challenge.week4.cli.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage

/**
 * Порт для автоматической генерации имени чата с помощью LLM.
 *
 * ## Архитектурная роль
 * - **Domain Port** — контракт, реализуемый в infrastructure-слое
 * - **Inversion of Control** — domain определяет интерфейс, infrastructure реализует
 *
 * Реализация должна анализировать первые сообщения диалога
 * (пользователя и ассистента), чтобы сгенерировать краткое,
 * информативное имя чата.
 */
interface ChatNameGenerator {

    /**
     * Генерирует имя чата на основе сообщений диалога.
     *
     * @param messages сообщения диалога для анализа
     * @return [Result] со сгенерированным именем или ошибкой
     */
    suspend fun generate(messages: List<ChatMessage>): Result<String>
}

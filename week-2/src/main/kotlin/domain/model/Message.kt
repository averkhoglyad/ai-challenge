package io.averkhogliad.ai.challenge.week2.domain.model

import java.time.Instant
import java.util.*

/**
 * Роль отправителя сообщения в диалоге.
 */
enum class MessageRole {
    /** Системное сообщение (инструкции для модели) */
    SYSTEM,

    /** Сообщение от пользователя */
    USER,

    /** Сообщение от ассистента (модели) */
    ASSISTANT
}

/**
 * Доменная модель сообщения в диалоговой сессии.
 *
 * ## Архитектурная роль
 * - **Domain Model** — сущность предметной области диалога
 * - **Immutable** — все изменения возвращают новый экземпляр
 *
 * ## Свойства
 * - [id] — уникальный идентификатор сообщения
 * - [sessionId] — идентификатор сессии, к которой принадлежит сообщение
 * - [role] — роль отправителя (SYSTEM, USER, ASSISTANT)
 * - [content] — текстовое содержимое сообщения
 * - [timestamp] — время создания сообщения
 */
data class Message(
    val id: String,
    val sessionId: SessionId,
    val role: MessageRole,
    val content: String,
    val timestamp: Instant
) {
    init {
        require(id.isNotBlank()) { "Message id cannot be blank" }
        require(content.isNotBlank()) { "Message content cannot be blank" }
    }

    companion object {
        /**
         * Создаёт новое сообщение с автоматически сгенерированным идентификатором.
         *
         * @param sessionId идентификатор сессии
         * @param role роль отправителя
         * @param content содержимое сообщения
         * @return новый экземпляр [Message]
         */
        fun create(
            sessionId: SessionId,
            role: MessageRole,
            content: String
        ): Message = Message(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role,
            content = content,
            timestamp = Instant.now()
        )
    }
}

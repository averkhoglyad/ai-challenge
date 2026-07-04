package io.averkhogliad.ai.challenge.week4.cli.domain.model

import java.time.Instant
import java.util.*

/**
 * Доменная модель сообщения в чате.
 *
 * ## Архитектурная роль
 * - **Sealed Interface** — исчерпывающий набор вариантов сообщений
 * - **Domain Model** — сущность предметной области чата
 *
 * ## Варианты
 * - [User] — сообщение от пользователя
 * - [Assistant] — ответ ассистента с опциональными цитатами и источниками
 * - [System] — системное сообщение (инструкции, уведомления)
 *
 * ## Свойства
 * - [id] — уникальный идентификатор сообщения
 * - [sessionId] — идентификатор сессии, к которой принадлежит сообщение
 * - [createdAt] — время создания сообщения
 */
sealed interface ChatMessage {
    val id: UUID
    val sessionId: UUID
    val createdAt: Instant

    /**
     * Сообщение от пользователя.
     *
     * @property text текст сообщения
     */
    data class User(
        override val id: UUID,
        override val sessionId: UUID,
        val text: String,
        override val createdAt: Instant
    ) : ChatMessage

    /**
     * Ответ ассистента (LLM).
     *
     * @property text текст ответа
     * @property citations номера цитат, на которые ссылается ответ (например, [1], [2])
     * @property sources источники цитат с метаданными
     */
    data class Assistant(
        override val id: UUID,
        override val sessionId: UUID,
        val text: String,
        val citations: List<Int>,
        val sources: List<ChatSource>,
        override val createdAt: Instant
    ) : ChatMessage

    /**
     * Системное сообщение (инструкции, уведомления, статус).
     *
     * @property text текст системного сообщения
     */
    data class System(
        override val id: UUID,
        override val sessionId: UUID,
        val text: String,
        override val createdAt: Instant
    ) : ChatMessage
}

/**
 * Источник цитаты в ответе ассистента.
 *
 * Связывает номер цитаты с конкретным документом и оценкой релевантности.
 *
 * @property citationNumber номер цитаты в тексте ответа
 * @property documentId идентификатор документа-источника
 * @property documentName читаемое имя документа
 * @property relevance оценка релевантности фрагмента
 */
data class ChatSource(
    val citationNumber: Int,
    val documentId: String,
    val documentName: String,
    val relevance: Float
)

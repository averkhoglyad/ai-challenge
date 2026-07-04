package io.averkhogliad.ai.challenge.week4.cli.domain.model

import java.time.Instant
import java.util.*

/**
 * Метаданные чат-сессии.
 *
 * ## Архитектурная роль
 * - **Value Object** — неизменяемые метаданные агрегата [ChatSession]
 *
 * ## Свойства
 * - [id] — уникальный идентификатор чата
 * - [name] — имя чата ("New Chat" по умолчанию, пока не сгенерировано автоимя)
 * - [nameGenerated] — флаг, было ли имя сгенерировано LLM или задано вручную
 * - [createdAt] — время создания чата
 * - [updatedAt] — время последнего обновления чата
 * - [archived] — флаг архивации
 * - [active] — флаг активности (только один чат может быть активным)
 */
data class ChatMetadata(
    val id: UUID,
    val name: String,
    val nameGenerated: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archived: Boolean,
    val active: Boolean
) {
    companion object {
        /**
         * Создаёт метаданные для нового чата с параметрами по умолчанию.
         *
         * @param name имя чата (по умолчанию "New Chat")
         * @return новый экземпляр [ChatMetadata]
         */
        fun create(name: String = "New Chat"): ChatMetadata {
            val now = Instant.now()
            return ChatMetadata(
                id = UUID.randomUUID(),
                name = name,
                nameGenerated = false,
                createdAt = now,
                updatedAt = now,
                archived = false,
                active = true
            )
        }
    }
}

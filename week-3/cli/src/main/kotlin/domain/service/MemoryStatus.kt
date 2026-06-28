package io.averkhogliad.ai.challenge.week3.cli.domain.service

import io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import java.time.Instant

/**
 * Модель статуса памяти для сессии диалога.
 *
 * ## Архитектурная роль
 * - **DTO** — модель для передачи информации о состоянии памяти
 * - **Immutable** — все изменения возвращают новый экземпляр
 *
 * ## Свойства
 * - [sessionId] — идентификатор сессии
 * - [level] — уровень сессии (TASK_LIST или TASK_DETAIL)
 * - [taskId] — опциональный идентификатор задачи (для TASK_DETAIL)
 * - [messageCount] — количество сообщений в STM
 * - [createdAt] — время создания сессии
 * - [updatedAt] — время последнего обновления сессии
 */
data class MemoryStatus(
    val sessionId: SessionId,
    val level: SessionLevel,
    val taskId: TaskId?,
    val messageCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Количество фактов в долговременной памяти (LTM). */
    val ltmFactCount: Int = 0
)

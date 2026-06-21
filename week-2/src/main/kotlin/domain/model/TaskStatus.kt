package io.averkhogliad.ai.challenge.week2.domain.model

/**
 * Статус задачи в системе управления задачами.
 *
 * Определяет жизненный цикл задачи:
 * - [OPEN] — задача открыта и требует выполнения
 * - [CLOSED] — задача успешно завершена
 * - [CANCELLED] — задача отменена
 */
enum class TaskStatus {
    OPEN,
    CLOSED,
    CANCELLED
}

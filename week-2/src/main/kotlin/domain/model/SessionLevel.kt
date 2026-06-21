package io.averkhogliad.ai.challenge.week2.domain.model

/**
 * Уровни сессии диалога в системе памяти.
 *
 * Определяет контекст, в котором ведётся диалог:
 * - [TASK_LIST] — сессия для работы со списком задач
 * - [TASK_DETAIL] — сессия для работы с конкретной задачей
 */
enum class SessionLevel {
    /** Сессия для работы со списком задач */
    TASK_LIST,

    /** Сессия для работы с конкретной задачей */
    TASK_DETAIL
}

package io.averkhogliad.ai.challenge.week4.cli.cli.chat

/**
 * Типизированное представление команд управления чатами и памятью задачи.
 *
 * Sealed interface обеспечивает исчерпывающую обработку в when-выражениях.
 */
sealed interface ChatCommand {
    /** Создать новый чат: :chat-new */
    data object New : ChatCommand

    /** Список чатов: :chat-list */
    data object List : ChatCommand

    /** Переключиться на чат: :chat-switch <id> */
    data class Switch(val id: String) : ChatCommand

    /** Переименовать чат: :chat-rename <name> */
    data class Rename(val name: String) : ChatCommand

    /** Удалить чат: :chat-delete <id> */
    data class Delete(val id: String) : ChatCommand

    /** Архивировать чат: :chat-archive */
    data object Archive : ChatCommand

    /** Показать историю диалога: :chat-history [N] */
    data class History(val limit: Int) : ChatCommand
}

/**
 * Команды управления памятью задачи ([TaskState]).
 */
sealed interface TaskStateCommand {
    /** Показать текущий TaskState: :task-state */
    data object Show : TaskStateCommand

    /** Сбросить память: :task-reset */
    data object Reset : TaskStateCommand

    /** Установить цель: :task-goal <text> */
    data class SetGoal(val text: String) : TaskStateCommand

    /** Добавить термин: :task-term add <name> <definition> */
    data class AddTerm(val name: String, val definition: String) : TaskStateCommand

    /** Удалить термин: :task-term remove <name> */
    data class RemoveTerm(val name: String) : TaskStateCommand

    /** Добавить ограничение: :task-constraint add <text> */
    data class AddConstraint(val text: String) : TaskStateCommand

    /** Удалить ограничение: :task-constraint remove <index> */
    data class RemoveConstraint(val index: Int) : TaskStateCommand
}

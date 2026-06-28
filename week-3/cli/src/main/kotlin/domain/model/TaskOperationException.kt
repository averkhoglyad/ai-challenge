package io.averkhogliad.ai.challenge.week3.cli.domain.model

/**
 * Исключения, связанные с операциями над задачами.
 */

class NoOpenTaskException : Exception("Требуется открытая задача. Используйте :open <id> перед планированием.")
class InvalidDateException(message: String) : Exception(message)
class FSMActiveException(val activeState: String) :
    Exception("Активная FSM-команда: $activeState. Завершите или отмените через :abort.")

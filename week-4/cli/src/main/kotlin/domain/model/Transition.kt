package io.averkhogliad.ai.challenge.week4.cli.domain.model

/**
 * Исключение, выбрасываемое при попытке совершить недопустимый переход.
 */
class TransitionNotAllowedException(
    val from: CommandStage,
    val to: CommandStage,
    val validationResult: TransitionValidationResult
) : IllegalStateException(
    "Transition not allowed: $from → $to. Reason: ${validationResult.reason}"
)

/**
 * Модель перехода между двумя состояниями FSM.
 *
 * Каждый переход описывает:
 * - Исходное состояние (from)
 * - Целевое состояние (to)
 * - Предусловие (condition) — лямбда, проверяющая контекст выполнения
 * - Описание (description) — человекочитаемое описание перехода
 *
 * ## Использование
 * Переходы определяются статически для каждой команды и передаются в TransitionValidator.
 *
 * @property from исходное состояние
 * @property to целевое состояние
 * @property condition предусловие перехода (лямбда, принимающая контекст и возвращающая Boolean)
 * @property description человекочитаемое описание перехода
 * @property reasonIfBlocked причина, по которой переход может быть заблокирован
 */
data class Transition(
    val from: CommandStage,
    val to: CommandStage,
    val condition: (Map<String, String>) -> Boolean,
    val description: String,
    val reasonIfBlocked: String = ""
) {
    /**
     * Проверяет, допустим ли переход для заданного контекста.
     */
    fun isAllowed(context: Map<String, String>): Boolean = condition(context)
}

/**
 * Результат валидации перехода.
 */
data class TransitionValidationResult(
    val allowed: Boolean,
    val reason: String
)

/**
 * Информация об одном состоянии для карты состояний.
 */
data class StateInfo(
    val state: CommandStage,
    val isCurrent: Boolean,
    val isAvailable: Boolean,
    val reason: String
)

/**
 * Карта состояний — полное описание графа для текущего состояния.
 */
data class StateMap(
    val currentState: CommandStage,
    val states: List<StateInfo>,
    val availableTransitions: List<Transition>
)

/**
 * Запись в истории переходов FSM.
 */
data class TransitionRecord(
    val from: CommandStage,
    val to: CommandStage,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String = ""
)

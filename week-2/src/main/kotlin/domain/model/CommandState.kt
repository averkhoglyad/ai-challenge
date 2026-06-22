package io.averkhogliad.ai.challenge.week2.domain.model

/**
 * Состояние выполняемой команды в FSM (Finite State Machine).
 *
 * Хранит всю информацию о текущем этапе выполнения сложной команды:
 * - имя команды
 * - текущий этап (stage)
 * - текущий шаг внутри этапа (step)
 * - ожидаемое действие (expectedAction)
 * - контекст выполнения (промежуточные данные)
 *
 * ## Жизненный цикл
 * - Создаётся при вызове сложной команды (например, :plan)
 * - Обновляется при переходе между этапами/шагами
 * - Уничтожается после завершения (DONE) или отмены (:abort)
 *
 * ## Immutable
 * Все изменения возвращают новый экземпляр через copy().
 */
data class CommandState(
    /** Имя выполняемой команды (например, "plan") */
    val commandName: String,

    /** Текущий этап выполнения */
    val currentStage: CommandStage,

    /** Текущий шаг внутри этапа (1-based) */
    val currentStep: Int = 1,

    /** Описание ожидаемого действия на текущем шаге */
    val expectedAction: String = "",

    /** Контекст выполнения — промежуточные данные, собираемые по ходу работы */
    val context: Map<String, String> = emptyMap()
) {
    init {
        require(commandName.isNotBlank()) { "Command name cannot be blank" }
        require(currentStep >= 1) { "Current step must be >= 1" }
    }

    /**
     * Переход к следующему этапу с сбросом шага на 1.
     */
    fun advanceToStage(stage: CommandStage, expectedAction: String = ""): CommandState =
        copy(currentStage = stage, currentStep = 1, expectedAction = expectedAction)

    /**
     * Переход к следующему шагу внутри текущего этапа.
     */
    fun advanceStep(expectedAction: String = ""): CommandState =
        copy(currentStep = currentStep + 1, expectedAction = expectedAction)

    /**
     * Сохранение значения в контекст выполнения.
     */
    fun putContext(key: String, value: String): CommandState =
        copy(context = context + (key to value))

    /**
     * Получение значения из контекста.
     */
    fun getContext(key: String): String? = context[key]

    /**
     * Проверка, завершена ли команда (этап DONE).
     */
    fun isDone(): Boolean = currentStage == CommandStage.DONE
}

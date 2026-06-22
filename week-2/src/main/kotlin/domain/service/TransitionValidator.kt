package io.averkhogliad.ai.challenge.week2.domain.service

import io.averkhogliad.ai.challenge.week2.domain.model.*

/**
 * Валидатор переходов FSM.
 *
 * Отвечает за:
 * - Проверку допустимости конкретного перехода (canTransition)
 * - Получение списка всех доступных переходов из заданного состояния (getAvailableTransitions)
 * - Объяснение причины недоступности перехода (getTransitionReason)
 * - Построение карты состояний (buildStateMap)
 *
 * ## Архитектурное расположение
 * Domain layer — pure logic, не зависит от инфраструктуры.
 *
 * ## Правила графа состояний для :plan
 * Допустимые переходы:
 * - PLANNING → EXECUTION (description заполнен, контекст собран)
 * - EXECUTION → VALIDATION (шаги получены от LLM)
 * - EXECUTION → PLANNING (откат при ошибке LLM)
 * - VALIDATION → DONE (пользователь подтвердил шаги)
 * - VALIDATION → EXECUTION (пользователь выбрал edit)
 * - DONE → TERMINATED (автоматически)
 *
 * Недопустимые переходы:
 * - PLANNING → DONE / VALIDATION (пропуск этапов)
 * - EXECUTION → DONE (пропуск VALIDATION)
 * - DONE → PLANNING / EXECUTION / VALIDATION (нельзя вернуться)
 * - TERMINATED → любое (команда завершена)
 */
class TransitionValidator(
    private val transitions: List<Transition>
) {
    /**
     * Проверяет, допустим ли переход между двумя состояниями.
     *
     * @param from исходное состояние
     * @param to целевое состояние
     * @param context контекст выполнения (пары ключ-значение из CommandState.context)
     * @return результат валидации с флагом allowed и причиной
     */
    fun canTransition(from: CommandStage, to: CommandStage, context: Map<String, String>): TransitionValidationResult {
        // Попытка перехода в текущее состояние
        if (from == to) {
            return TransitionValidationResult(
                allowed = false,
                reason = "Вы уже находитесь в состоянии $from"
            )
        }

        // Из TERMINATED нет переходов
        if (from == CommandStage.TERMINATED) {
            return TransitionValidationResult(
                allowed = false,
                reason = "Команда завершена (TERMINATED). Нет доступных переходов."
            )
        }

        // Попытка перехода в TERMINATED всегда разрешена (аварийное завершение)
        if (to == CommandStage.TERMINATED) {
            return TransitionValidationResult(
                allowed = true,
                reason = "Аварийное завершение команды"
            )
        }

        // Поиск перехода в списке допустимых
        val transition = transitions.find { it.from == from && it.to == to }
        if (transition == null) {
            return TransitionValidationResult(
                allowed = false,
                reason = getDefaultBlockReason(from, to)
            )
        }

        // Проверка предусловия
        val conditionMet = transition.isAllowed(context)
        return if (conditionMet) {
            TransitionValidationResult(allowed = true, reason = transition.description)
        } else {
            TransitionValidationResult(
                allowed = false,
                reason = transition.reasonIfBlocked.ifEmpty {
                    "Условие перехода не выполнено: ${transition.description}"
                }
            )
        }
    }

    /**
     * Возвращает список доступных переходов из заданного состояния.
     *
     * @param from исходное состояние
     * @param context контекст выполнения
     * @return список допустимых переходов (у которых выполнены предусловия)
     */
    fun getAvailableTransitions(from: CommandStage, context: Map<String, String>): List<Transition> {
        return transitions.filter { transition ->
            transition.from == from && transition.isAllowed(context)
        }
    }

    /**
     * Возвращает причину, по которой переход недоступен.
     *
     * @param from исходное состояние
     * @param to целевое состояние
     * @param context контекст выполнения
     * @return строка с причиной недоступности или null если переход доступен
     */
    fun getTransitionReason(from: CommandStage, to: CommandStage, context: Map<String, String>): String? {
        val result = canTransition(from, to, context)
        return if (result.allowed) null else result.reason
    }

    /**
     * Строит карту состояний для текущего состояния и контекста.
     *
     * @param currentState текущее состояние FSM
     * @param context контекст выполнения
     * @return StateMap с информацией о всех состояниях и доступных переходах
     */
    fun buildStateMap(currentState: CommandStage, context: Map<String, String>): StateMap {
        val allStates = CommandStage.entries
        val availableTransitions = getAvailableTransitions(currentState, context)

        val states = allStates.map { state ->
            val transition = transitions.find { it.from == currentState && it.to == state }
            val validationResult = canTransition(currentState, state, context)

            StateInfo(
                state = state,
                isCurrent = state == currentState,
                isAvailable = validationResult.allowed,
                reason = if (state == currentState) {
                    "(текущее)"
                } else if (validationResult.allowed) {
                    "(доступно - ${transition?.description ?: validationResult.reason})"
                } else {
                    "(недоступно - ${validationResult.reason})"
                }
            )
        }

        return StateMap(
            currentState = currentState,
            states = states,
            availableTransitions = availableTransitions
        )
    }

    /**
     * Возвращает все переходы (для отладки и тестирования).
     */
    fun getAllTransitions(): List<Transition> = transitions.toList()

    /**
     * Генерирует стандартную причину блокировки для неопределённого перехода.
     */
    private fun getDefaultBlockReason(from: CommandStage, to: CommandStage): String {
        return when {
            from == CommandStage.PLANNING && to == CommandStage.DONE ->
                "Нельзя пропустить этапы EXECUTION и VALIDATION"

            from == CommandStage.PLANNING && to == CommandStage.VALIDATION ->
                "Нельзя пропустить этап EXECUTION"

            from == CommandStage.EXECUTION && to == CommandStage.DONE ->
                "Нельзя пропустить этап VALIDATION"

            from == CommandStage.DONE && to != CommandStage.TERMINATED ->
                "Нельзя вернуться из завершённого состояния DONE"

            from == CommandStage.VALIDATION && to == CommandStage.PLANNING ->
                "Нельзя вернуться из VALIDATION в PLANNING"

            else -> "Переход $from → $to не определён в графе состояний"
        }
    }

    companion object {
        /**
         * Стандартный граф переходов для команды :plan.
         *
         * 6 допустимых переходов:
         * - PLANNING → EXECUTION (description заполнен)
         * - EXECUTION → VALIDATION (шаги получены)
         * - EXECUTION → PLANNING (откат при ошибке)
         * - VALIDATION → DONE (пользователь подтвердил)
         * - VALIDATION → EXECUTION (пользователь выбрал edit)
         * - DONE → TERMINATED (автоматически)
         */
        fun planTransitions(): List<Transition> = listOf(
            Transition(
                from = CommandStage.PLANNING,
                to = CommandStage.EXECUTION,
                condition = { context ->
                    // description должен быть заполнен
                    val needsDesc = context["needsDescription"]
                    needsDesc != "true"
                },
                description = "Контекст собран, переход к генерации шагов",
                reasonIfBlocked = "Необходимо заполнить description задачи"
            ),
            Transition(
                from = CommandStage.EXECUTION,
                to = CommandStage.VALIDATION,
                condition = { context ->
                    // Шаги должны быть получены от LLM
                    val stepsJson = context["generatedSteps"]
                    !stepsJson.isNullOrBlank()
                },
                description = "Шаги получены от LLM, переход к валидации",
                reasonIfBlocked = "Шаги ещё не получены от LLM"
            ),
            Transition(
                from = CommandStage.EXECUTION,
                to = CommandStage.PLANNING,
                condition = { true },
                description = "Откат при ошибке LLM",
                reasonIfBlocked = ""
            ),
            Transition(
                from = CommandStage.VALIDATION,
                to = CommandStage.DONE,
                condition = { context ->
                    val stepsJson = context["generatedSteps"]
                    !stepsJson.isNullOrBlank()
                },
                description = "Пользователь подтвердил шаги, сохранение в WM",
                reasonIfBlocked = "Шаги не найдены в контексте"
            ),
            Transition(
                from = CommandStage.VALIDATION,
                to = CommandStage.EXECUTION,
                condition = { true },
                description = "Пользователь выбрал редактирование шагов",
                reasonIfBlocked = ""
            ),
            Transition(
                from = CommandStage.DONE,
                to = CommandStage.TERMINATED,
                condition = { true },
                description = "Автоматическое завершение команды",
                reasonIfBlocked = ""
            )
        )
    }
}

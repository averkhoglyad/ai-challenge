package io.averkhogliad.ai.challenge.week2.cli.renderers

import io.averkhogliad.ai.challenge.week2.cli.renderers.ConsoleColors.CYAN
import io.averkhogliad.ai.challenge.week2.cli.renderers.ConsoleColors.GREEN
import io.averkhogliad.ai.challenge.week2.cli.renderers.ConsoleColors.RED
import io.averkhogliad.ai.challenge.week2.cli.renderers.ConsoleColors.RESET
import io.averkhogliad.ai.challenge.week2.cli.renderers.ConsoleColors.YELLOW
import io.averkhogliad.ai.challenge.week2.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week2.domain.model.CommandState
import io.averkhogliad.ai.challenge.week2.domain.model.StateMap
import io.averkhogliad.ai.challenge.week2.domain.model.Transition

/**
 * Специализированный рендерер для FSM (Finite State Machine) — состояния, переходы, goto, debug.
 *
 * Выделен из [ConsoleCliRenderer] для соблюдения Single Responsibility Principle.
 * Все методы чисто функциональные (рендерят в stdout), без зависимостей — легко тестируются.
 */
class FsmRenderer {

    // ──── FSM visualization ────

    fun renderFsmState(state: CommandState) {
        println()
        println("${CYAN}🔧 [DEBUG] Команда: ${state.commandName}${RESET}")
        println("${CYAN}🔧 [DEBUG] Этап: ${state.currentStage}${RESET}")
        println("${CYAN}🔧 [DEBUG] Шаг: ${state.currentStep}${RESET}")
        if (state.expectedAction.isNotEmpty()) {
            println("${CYAN}🔧 [DEBUG] Действие: ${state.expectedAction}${RESET}")
        }
        if (state.context.isNotEmpty()) {
            println("${CYAN}🔧 [DEBUG] Контекст:${RESET}")
            state.context.forEach { (key, value) ->
                println("  $key: $value")
            }
        }
        println()
    }

    // ──── FSM state command rendering (:state) ────

    fun renderFsmStateInfo(state: CommandState) {
        println()
        println("${CYAN}=== ⚙️  Состояние FSM ===${RESET}")
        println("${CYAN}Команда:${RESET} ${state.commandName}")
        println("${CYAN}Этап:${RESET} ${state.currentStage}")
        println("${CYAN}Шаг:${RESET} ${state.currentStep}")
        if (state.expectedAction.isNotEmpty()) {
            println("${CYAN}Ожидаемое действие:${RESET} ${state.expectedAction}")
        }
        if (state.context.isNotEmpty()) {
            println("${CYAN}Контекст:${RESET}")
            state.context.forEach { (key, value) ->
                println("  $key: $value")
            }
        }
        println()
    }

    fun renderNoActiveCommand() {
        println()
        println("${YELLOW}⚠️  Нет активной команды${RESET}")
        println()
    }

    // ──── Abort command rendering (:abort) ────

    fun renderAbortConfirmation() {
        println()
        print("Прервать выполнение команды? (y/n): ")
        System.out.flush()
    }

    fun renderAbortSuccess() {
        println()
        println("${GREEN}✅ Команда прервана${RESET}")
        println()
    }

    fun renderAbortCancelled() {
        println()
        println("${YELLOW}⚠️  Прерывание отменено${RESET}")
        println()
    }

    // ──── FSM status in :status command ────

    fun renderStatusFsm(stage: CommandStage?, availableTransitions: List<Transition>) {
        if (stage != null && availableTransitions.isNotEmpty()) {
            val transitionNames = availableTransitions.joinToString(", ") { it.to.name }
            println("  ${CYAN}⚙️  FSM:${RESET} :plan в состоянии $stage (доступно: $transitionNames)")
        } else if (stage != null) {
            println("  ${CYAN}⚙️  FSM:${RESET} :plan в состоянии $stage (доступно: нет)")
        } else {
            println("  ${YELLOW}⚙️  FSM:${RESET} нет активной команды")
        }
    }

    // ──── Goto command rendering methods ────

    fun renderStateMap(stateMap: StateMap) {
        println()
        println("${CYAN}=== 🗺️  Карта состояний FSM ===${RESET}")
        println("${CYAN}⚙️  Активная команда:${RESET} :plan")
        println("${CYAN}📍 Текущее состояние:${RESET} ● ${stateMap.currentState}")
        println()

        println("${CYAN}📋 Состояния:${RESET}")
        for (stateInfo in stateMap.states) {
            val marker = if (stateInfo.isCurrent) "${GREEN}●${RESET}" else "○"
            val availability = if (stateInfo.isCurrent) " ${GREEN}(текущее)${RESET}" else stateInfo.reason
            println("  $marker ${stateInfo.state} $availability")
        }

        println()
        if (stateMap.availableTransitions.isNotEmpty()) {
            println("${CYAN}➡️  Доступные переходы:${RESET}")
            for (transition in stateMap.availableTransitions) {
                println("  ${CYAN}→${RESET} ${transition.to} — ${transition.description}")
            }
        } else {
            println("${YELLOW}⚠️  Доступные переходы: нет${RESET}")
        }
        println()
    }

    fun renderGotoSuccess(from: CommandStage, to: CommandStage) {
        println()
        println("${GREEN}✅ Переход $from → $to выполнен${RESET}")
        println()
    }

    fun renderGotoError(reason: String) {
        println()
        println("${RED}❌ Ошибка перехода: $reason${RESET}")
        println()
    }

    fun renderGotoNoActiveCommand() {
        println()
        println("${YELLOW}⚠️  Нет активной команды${RESET}")
        println()
    }

    fun renderGotoInvalidState(stateName: String) {
        println()
        println("${RED}❌ Некорректное состояние: \"$stateName\"${RESET}")
        println("${CYAN}Допустимые состояния:${RESET} ${CommandStage.entries.joinToString(", ") { it.name }}")
        println()
    }

    // ──── Debug mode — available transitions rendering ────

    fun renderAvailableTransitions(transitions: List<Transition>) {
        if (transitions.isNotEmpty()) {
            println("  ${CYAN}➡️  Доступные переходы:${RESET}")
            for (transition in transitions) {
                println("  ${CYAN}→${RESET} ${transition.to} (${transition.description})")
            }
        }
    }

    // ──── Status display helpers ────

    fun renderStatusDebug(enabled: Boolean) {
        println()
        if (enabled) {
            println("${GREEN}🔧 Debug mode: enabled${RESET}")
        } else {
            println("${YELLOW}🔧 Debug mode: disabled${RESET}")
        }
        println()
    }

    fun renderStatusActiveCommand(commandName: String?) {
        println()
        if (commandName != null) {
            println("${CYAN}⚡ Активная команда: $commandName${RESET}")
        } else {
            println("${YELLOW}⚡ Активная команда: нет${RESET}")
        }
        println()
    }
}

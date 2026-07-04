package io.averkhogliad.ai.challenge.week4.cli.cli.chat

import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Рендерер состояния памяти задачи ([TaskState]):
 * цель, термины, ограничения, факты.
 *
 * Не зависит от Mordant напрямую — использует ANSI-коды и println.
 */
object TaskStateRenderer {

    private val RESET = "\u001b[0m"
    private val CYAN = "\u001b[36m"
    private val GREEN = "\u001b[32m"
    private val YELLOW = "\u001b[33m"
    private val DIM = "\u001b[2m"
    private val BOLD = "\u001b[1m"

    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    /**
     * Рендерит состояние памяти задачи.
     */
    fun render(state: TaskState) {
        val separator = "━".repeat(55)

        println()
        println(separator)
        println("${BOLD}Память задачи${RESET}")
        println("${DIM}Последнее обновление: ${timeFormatter.format(state.lastUpdated)}${RESET}")
        println(separator)

        // Цель
        if (state.goal != null) {
            println()
            println("${BOLD}Цель:${RESET}")
            println("  $CYAN${state.goal}$RESET")
        } else {
            println()
            println("${DIM}Цель не задана.${RESET}")
            println("  Используйте ${CYAN}:task-goal <текст>${RESET} для установки цели.")
        }

        // Термины
        println()
        if (state.definedTerms.isNotEmpty()) {
            println("${BOLD}Термины (${state.definedTerms.size}):${RESET}")
            for ((name, definition) in state.definedTerms) {
                println("  ${GREEN}$name${RESET} — $definition")
            }
        } else {
            println("${DIM}Термины не заданы.${RESET}")
            println("  Используйте ${CYAN}:task-term add <имя> <определение>${RESET}")
        }

        // Ограничения
        println()
        if (state.constraints.isNotEmpty()) {
            println("${BOLD}Ограничения (${state.constraints.size}):${RESET}")
            for ((index, constraint) in state.constraints.withIndex()) {
                println("  ${YELLOW}[$index]${RESET} $constraint")
            }
        } else {
            println("${DIM}Ограничения не заданы.${RESET}")
            println("  Используйте ${CYAN}:task-constraint add <текст>${RESET}")
        }

        // Уточнённые факты
        println()
        if (state.clarifiedFacts.isNotEmpty()) {
            println("${BOLD}Уточнённые факты (${state.clarifiedFacts.size}):${RESET}")
            for (fact in state.clarifiedFacts) {
                println("  • $fact")
            }
        } else {
            println("${DIM}Уточнённые факты отсутствуют.${RESET}")
        }

        println()
        println(separator)
    }
}

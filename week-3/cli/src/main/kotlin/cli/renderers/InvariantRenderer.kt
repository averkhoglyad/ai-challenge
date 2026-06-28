package io.averkhogliad.ai.challenge.week3.cli.cli.renderers

import io.averkhogliad.ai.challenge.week3.cli.cli.renderers.ConsoleColors.CYAN
import io.averkhogliad.ai.challenge.week3.cli.cli.renderers.ConsoleColors.GREEN
import io.averkhogliad.ai.challenge.week3.cli.cli.renderers.ConsoleColors.RED
import io.averkhogliad.ai.challenge.week3.cli.cli.renderers.ConsoleColors.RESET
import io.averkhogliad.ai.challenge.week3.cli.cli.renderers.ConsoleColors.YELLOW
import io.averkhogliad.ai.challenge.week3.cli.domain.model.Invariant

/**
 * Специализированный рендерер для инвариантов.
 *
 * Выделен из [ConsoleCliRenderer] для соблюдения Single Responsibility Principle.
 */
class InvariantRenderer {

    fun renderInvariantList(invariants: List<Invariant>) {
        println()
        if (invariants.isEmpty()) {
            println("${YELLOW}⚠️  Инварианты не заданы${RESET}")
        } else {
            println("${CYAN}🛡️  Активные инварианты:${RESET}")
            invariants.forEach { inv ->
                println("  ${CYAN}${inv.id.value}.${RESET} ${inv.rule}")
            }
        }
        println()
    }

    fun renderInvariantAdded(invariant: Invariant) {
        println()
        println("${GREEN}✅ Инвариант #${invariant.id.value} добавлен${RESET}")
        println()
    }

    fun renderInvariantRemoved(id: Int) {
        println()
        println("${GREEN}✅ Инвариант #$id удалён${RESET}")
        println()
    }

    fun renderInvariantNotFound(id: Int) {
        println()
        println("${RED}❌ Инвариант #$id не найден${RESET}")
        println()
    }

    fun renderInvariantEmptyRule() {
        println()
        println("${RED}❌ Инвариант не может быть пустым${RESET}")
        println()
    }

    fun renderInvariantRemoveConfirmation(id: Int) {
        println()
        print("${YELLOW}⚠️  Удалить инвариант #$id? Это расширит границы дозволенного для агента. (y/n): ${RESET}")
        System.out.flush()
    }

    fun renderStatusInvariants(count: Int) {
        println("  ${CYAN}🛡️  Инварианты:${RESET} $count")
    }
}

package io.averkhogliad.ai.challenge.week4.cli.cli.chat

import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatSession
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Рендерер списка чат-сессий с маркерами (активный, архивированный).
 *
 * Не зависит от Mordant напрямую — использует ANSI-коды и println.
 */
object ChatListRenderer {

    private val RESET = "\u001b[0m"
    private val CYAN = "\u001b[36m"
    private val GREEN = "\u001b[32m"
    private val YELLOW = "\u001b[33m"
    private val DIM = "\u001b[2m"
    private val BOLD = "\u001b[1m"

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    /**
     * Рендерит список чат-сессий в виде таблицы.
     *
     * @param sessions список сессий
     * @param activeId ID активной сессии (для маркера)
     */
    fun render(sessions: List<ChatSession>, activeId: UUID?) {
        if (sessions.isEmpty()) {
            println("${DIM}Нет доступных чатов.${RESET}")
            println("Используйте ${CYAN}:chat-new${RESET} для создания нового чата.")
            return
        }

        val separator = "━".repeat(72)
        println()
        println(separator)
        println("${BOLD}Список чатов (${sessions.size})${RESET}")
        println(separator)

        // Заголовок таблицы
        println(
            String.format(
                "  %-2s  %-36s  %-12s  %-8s",
                "#", "Имя", "Обновлён", "Статус"
            )
        )
        println("  ${"─".repeat(68)}")

        for ((index, session) in sessions.withIndex()) {
            val num = index + 1
            val name = session.metadata.name
            val updated = dateFormatter.format(session.metadata.updatedAt)
            val isActive = session.metadata.id == activeId
            val isArchived = session.metadata.archived

            val status = when {
                isActive -> "${GREEN}▶ активен${RESET}"
                isArchived -> "${DIM}архив${RESET}"
                else -> "${DIM}неактивен${RESET}"
            }

            val nameMarker = if (session.metadata.nameGenerated) "${DIM}[auto]${RESET}" else ""
            val displayName = if (name.length > 32) name.take(29) + "..." else name.padEnd(36)

            println(
                String.format(
                    "  %-2d  %s %s ${DIM}%-12s${RESET}  %s",
                    num, CYAN + displayName + RESET, nameMarker,
                    updated, status
                )
            )
        }

        println(separator)
        println()
        println("${DIM}Команды:${RESET} ${CYAN}:chat-new${RESET} | ${CYAN}:chat-switch <id>${RESET} | ${CYAN}:chat-rename <name>${RESET} | ${CYAN}:chat-delete <id>${RESET} | ${CYAN}:chat-archive${RESET}")
        println()
    }
}

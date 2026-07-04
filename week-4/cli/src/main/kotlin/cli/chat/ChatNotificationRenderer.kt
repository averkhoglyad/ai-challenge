package io.averkhogliad.ai.challenge.week4.cli.cli.chat

import java.util.*

/**
 * Рендерер уведомлений: «Чат создан», «Переключено на...», «Память обновлена».
 *
 * Не зависит от Mordant напрямую — использует ANSI-коды и println.
 */
object ChatNotificationRenderer {

    private val RESET = "\u001b[0m"
    private val CYAN = "\u001b[36m"
    private val GREEN = "\u001b[32m"
    private val YELLOW = "\u001b[33m"
    private val DIM = "\u001b[2m"

    /**
     * Уведомление: чат создан.
     */
    fun renderChatCreated(name: String, id: UUID) {
        println()
        println("${GREEN}✓${RESET} Чат создан: ${CYAN}$name${RESET} ${DIM}(${id.toString().take(8)}...)${RESET}")
        println()
    }

    /**
     * Уведомление: переключено на чат.
     */
    fun renderSwitchedTo(name: String, id: UUID) {
        println()
        println("${GREEN}✓${RESET} Переключено на: ${CYAN}$name${RESET} ${DIM}(${id.toString().take(8)}...)${RESET}")
        println()
    }

    /**
     * Уведомление: чат переименован.
     */
    fun renderChatRenamed(name: String) {
        println()
        println("${GREEN}✓${RESET} Чат переименован в: ${CYAN}$name${RESET}")
        println()
    }

    /**
     * Уведомление: чат удалён.
     */
    fun renderChatDeleted(id: UUID) {
        println()
        println("${GREEN}✓${RESET} Чат удалён: ${DIM}(${id.toString().take(8)}...)${RESET}")
        println()
    }

    /**
     * Уведомление: чат архивирован.
     */
    fun renderChatArchived(id: UUID) {
        println()
        println("${GREEN}✓${RESET} Чат архивирован: ${DIM}(${id.toString().take(8)}...)${RESET}")
        println()
    }

    /**
     * Уведомление: память обновлена.
     */
    fun renderTaskStateUpdated(detail: String) {
        println("${GREEN}✓${RESET} Память обновлена: $detail")
    }

    /**
     * Уведомление: память сброшена.
     */
    fun renderTaskStateReset() {
        println()
        println("${GREEN}✓${RESET} Память задачи сброшена.")
        println()
    }

    /**
     * Уведомление: вход в режим чата.
     */
    fun renderChatModeEntered(name: String) {
        val separator = "━".repeat(55)
        println()
        println(separator)
        println("${CYAN}Режим чата активирован${RESET}")
        println("  Чат: ${CYAN}$name${RESET}")
        println()
        println("  ${DIM}Вводите текст для общения. Команды начинаются с ${CYAN}:${RESET}")
        println("  ${DIM}Доступные команды:${RESET} ${CYAN}:chat-new${RESET} ${CYAN}:chat-list${RESET} ${CYAN}:chat-rename${RESET} ${CYAN}:chat-history${RESET} ${CYAN}:task-state${RESET} ${CYAN}:back${RESET} ${CYAN}:quit${RESET}")
        println(separator)
        println()
    }

    /**
     * Уведомление: выход из режима чата.
     */
    fun renderChatModeExited() {
        println()
        println("${GREEN}✓${RESET} Выход из режима чата.")
        println()
    }

    /**
     * Сообщение об ошибке.
     */
    fun renderError(message: String) {
        println("${YELLOW}⚠${RESET} $message")
    }

    /**
     * Информационное сообщение.
     */
    fun renderInfo(message: String) {
        println("$DIM$message$RESET")
    }
}

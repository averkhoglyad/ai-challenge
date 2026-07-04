package io.averkhogliad.ai.challenge.week4.cli.cli.chat

import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Рендерер истории диалога — последние N сообщений.
 *
 * Не зависит от Mordant напрямую — использует ANSI-коды и println.
 * Весь пользовательский текст — на русском языке.
 */
object ChatHistoryRenderer {

    private val RESET = "\u001b[0m"
    private val CYAN = "\u001b[36m"
    private val GREEN = "\u001b[32m"
    private val DIM = "\u001b[2m"
    private val BOLD = "\u001b[1m"

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())

    /**
     * Рендерит последние N сообщений диалога.
     *
     * @param messages полный список сообщений
     * @param limit максимальное количество для отображения
     */
    fun render(messages: List<ChatMessage>, limit: Int = 10) {
        if (messages.isEmpty()) {
            println("${DIM}История диалога пуста.${RESET}")
            return
        }

        val recent = messages.takeLast(limit)
        val separator = "━".repeat(55)

        println()
        println("$separator")
        println("${BOLD}История диалога (последние ${recent.size} из ${messages.size})${RESET}")
        println(separator)

        for (message in recent) {
            val time = timeFormatter.format(message.createdAt)
            when (message) {
                is ChatMessage.User -> {
                    println("${CYAN}[User $time]${RESET}")
                    println(message.text)
                    println()
                }

                is ChatMessage.Assistant -> {
                    println("${GREEN}[Assistant $time]${RESET}")
                    println(message.text)
                    if (message.sources.isNotEmpty()) {
                        println("${DIM}  Источники: ${message.sources.joinToString { "[${it.citationNumber}] ${it.documentName}" }}${RESET}")
                    }
                    println()
                }

                is ChatMessage.System -> {
                    println("${DIM}[System $time]${RESET} ${message.text}")
                    println()
                }
            }
        }

        println(separator)
    }

    /**
     * Рендерит сообщение об очистке истории.
     */
    fun renderHistoryCleared(messageCount: Int) {
        println("${DIM}История диалога очищена ($messageCount сообщений). Память задачи сохранена.${RESET}")
    }
}

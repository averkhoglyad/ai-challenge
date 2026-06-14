package io.averkhogliad.ai.challenge.week1.domain.context

import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage

/**
 * Результат сжатия контекста диалога.
 *
 * Содержит суммаризацию старых сообщений (если есть) и последние N сообщений
 * в несжатом виде. Используется [DialogContextCompressor] для подготовки
 * контекста перед отправкой в LLM.
 *
 * ## Алгоритм формирования
 * 1. Если есть [summary] — оно добавляется как часть system-сообщения
 * 2. [recentMessages] — последние N (windowSize) сообщений в несжатом виде
 * 3. [compressedMessageCount] — сколько исходных сообщений было сжато в summary
 *
 * @property summary текст суммаризации предыдущих сообщений (null, если сжатие не применялось)
 * @property recentMessages последние сообщения диалога в несжатом виде
 * @property compressedMessageCount количество сообщений, сжатых в summary
 */
data class DialogContext(
    val summary: String?,
    val recentMessages: List<ChatMessage>,
    val compressedMessageCount: Int = 0
) {
    init {
        require(compressedMessageCount >= 0) {
            "compressedMessageCount must be non-negative, got $compressedMessageCount"
        }
    }

    /**
     * Преобразует сжатый контекст в список сообщений для отправки в LLM.
     *
     * ## Логика формирования
     * - Если [summary] есть — создаётся одно system-сообщение,
     *   объединяющее [systemPrompt] и summary
     * - Затем добавляются [recentMessages] с ролями user/assistant
     *
     * @param systemPrompt базовый system prompt (инструкции модели)
     * @return список [io.averkhogliad.ai.challenge.utils.llm.ChatMessage] для отправки в LLM API
     */
    fun toMessagesList(systemPrompt: String): List<io.averkhogliad.ai.challenge.utils.llm.ChatMessage> {
        val messages = mutableListOf<io.averkhogliad.ai.challenge.utils.llm.ChatMessage>()

        // Формируем system-сообщение: systemPrompt + summary (если есть)
        val systemContent = if (summary != null) {
            "$systemPrompt\n\n[Context summary of earlier conversation]\n$summary"
        } else {
            systemPrompt
        }
        messages.add(io.averkhogliad.ai.challenge.utils.llm.ChatMessage.system(systemContent))

        // Добавляем recentMessages, конвертируя из domain.ChatMessage в utils.ChatMessage
        for (msg in recentMessages) {
            messages.add(
                io.averkhogliad.ai.challenge.utils.llm.ChatMessage(
                    role = msg.role.roleName,
                    content = msg.content
                )
            )
        }

        return messages
    }

    /**
     * Грубая оценка количества токенов в контексте.
     *
     * Использует эвристику ~4 символа на токен для английского текста.
     * Учитывает [summary] и все [recentMessages].
     *
     * @return примерное количество токенов
     */
    fun estimateTokenCount(): Int {
        val summaryChars = summary?.length ?: 0
        val messagesChars = recentMessages.sumOf { it.content.length }
        val totalChars = summaryChars + messagesChars
        // ~4 символа на токен — грубая оценка
        return if (totalChars == 0) 0 else maxOf(1, totalChars / 4)
    }
}

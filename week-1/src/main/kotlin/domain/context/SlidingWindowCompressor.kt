package io.averkhogliad.ai.challenge.week1.domain.context

import io.averkhogliad.ai.challenge.week1.domain.ModelId
import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfig
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import io.averkhogliad.ai.challenge.week1.domain.service.LlmPort

/**
 * Реализация [DialogContextCompressor] на основе скользящего окна с LLM-суммаризацией.
 *
 * Алгоритм:
 * 1. Если сообщений <= windowSize — сжатие не требуется, возвращаем контекст без изменений
 * 2. Определяем "старые" сообщения (за пределами окна): dropLast(windowSize)
 * 3. Берём последние blockSize сообщений из "старых" для суммаризации
 * 4. Если previousSummary == null — первичная суммаризация блока
 * 5. Если previousSummary != null — инкрементальное обновление summary
 * 6. Вызываем LLM для получения текста summary
 * 7. Возвращаем DialogContext с новым summary, последними N сообщениями и счётчиком сжатых
 *
 * @property llmPort порт для взаимодействия с LLM при суммаризации
 */
class SlidingWindowCompressor(
    private val llmPort: LlmPort
) : DialogContextCompressor {

    override suspend fun compress(
        messages: List<ChatMessage>,
        config: ContextCompressionConfig,
        previousSummary: String?
    ): DialogContext {
        // Edge case: empty or small message list — no compression needed
        if (messages.size <= config.windowSize) {
            return DialogContext(
                summary = previousSummary,
                recentMessages = messages,
                compressedMessageCount = 0
            )
        }

        // Determine "old" messages (outside the sliding window)
        val oldMessages = messages.dropLast(config.windowSize)

        // Determine the block to summarize: last blockSize messages from old
        val block = if (oldMessages.size <= config.blockSize) {
            oldMessages
        } else {
            oldMessages.takeLast(config.blockSize)
        }

        // Build the appropriate prompt
        val promptText = if (previousSummary == null) {
            SummaryPromptBuilder.buildInitialSummaryPrompt(block)
        } else {
            SummaryPromptBuilder.buildIncrementalSummaryPrompt(previousSummary, block)
        }

        // Call LLM to get the summary
        val newSummary = callLlmForSummary(promptText, config)

        // Return the compressed context
        return DialogContext(
            summary = newSummary,
            recentMessages = messages.takeLast(config.windowSize),
            compressedMessageCount = block.size
        )
    }

    /**
     * Вызывает LLM для получения текста суммаризации.
     *
     * @param promptText текст промпта для суммаризации
     * @param config конфигурация сжатия (для извлечения summaryModelId)
     * @return текст summary от LLM
     */
    private suspend fun callLlmForSummary(promptText: String, config: ContextCompressionConfig): String {
        val prompt = Prompt(promptText)
        // Use low temperature for deterministic summarization
        val taskConfig = TaskExecutionConfig(
            temperature = 0.3,
            maxTokens = 1000,
            modelId = config.summaryModelId?.takeIf { it.isNotBlank() }?.let { ModelId(it) }
        )

        return when (val result = llmPort.chat(prompt, taskConfig)) {
            is TaskResult.Success -> result.content
            is TaskResult.Partial -> result.content
            is TaskResult.Error -> {
                // Fallback: if LLM fails, return a placeholder summary
                "[Summary generation failed: ${result.message}]"
            }
        }
    }
}

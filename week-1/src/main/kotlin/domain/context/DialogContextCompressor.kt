package io.averkhogliad.ai.challenge.week1.domain.context

import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfig
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage

/**
 * Интерфейс сжатия контекста диалога (стратегия).
 *
 * Определяет контракт для алгоритмов сжатия истории сообщений.
 * Реализации должны обеспечивать сохранение семантической связности
 * диалога при уменьшении объёма контекста.
 *
 * ## Жизненный цикл
 * 1. Вызывается перед каждой отправкой сообщений в LLM
 * 2. Получает полный список сообщений диалога и текущую конфигурацию
 * 3. Возвращает [DialogContext] с суммаризацией и последними сообщениями
 *
 * ## Контракт
 * - Если [ContextCompressionConfig.enabled] == false — возвращать контекст без сжатия
 * - [previousSummary] может быть null при первом вызове
 * - Результат [DialogContext.summary] включается в system-сообщение
 *
 * @see SlidingWindowCompressor
 */
interface DialogContextCompressor {

    /**
     * Сжимает историю сообщений диалога.
     *
     * @param messages полный список сообщений диалога (в хронологическом порядке)
     * @param config текущая конфигурация сжатия
     * @param previousSummary предыдущая суммаризация для накопительного сжатия (null при первом вызове)
     * @return [DialogContext] — сжатый контекст для отправки в LLM
     */
    suspend fun compress(
        messages: List<ChatMessage>,
        config: ContextCompressionConfig,
        previousSummary: String?
    ): DialogContext
}
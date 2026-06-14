package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.model.Dialog

/**
 * Базовый интерфейс стратегии управления контекстом диалога.
 *
 * Определяет контракт для алгоритмов управления контекстом, передаваемым в LLM.
 * Каждая стратегия решает, какие сообщения и в каком виде включать в контекст.
 *
 * ## Жизненный цикл
 * 1. [processUserMessage] — вызывается при получении нового сообщения от пользователя.
 *    Стратегия может извлечь факты, создать чекпоинт и т.д.
 * 2. [prepareContext] — вызывается перед отправкой в LLM.
 *    Формирует итоговый список сообщений для LLM-вызова.
 *
 * ## Контракт
 * - Реализации могут иметь внутреннее состояние. При использовании в многопоточной среде
 *   необходима внешняя синхронизация или использование отдельных экземпляров для каждого потока.
 * - [prepareContext] не должен модифицировать переданный [Dialog]
 * - Результат [prepareContext] включает system prompt + messages для LLM
 *
 * @see SlidingWindowStrategy
 * @see StickyFactsStrategy
 * @see BranchingStrategy
 */
interface ContextManagementStrategy {

    /** Уникальное имя стратегии (для CLI и конфигурации) */
    val name: String

    /** Человекочитаемое описание стратегии */
    val description: String

    /**
     * Обрабатывает новое сообщение пользователя.
     *
     * Вызывается до [prepareContext]. Стратегия может:
     * - Извлечь факты из сообщения (StickyFacts)
     * - Создать чекпоинт (Branching)
     * - Ничего не делать (SlidingWindow)
     *
     * @param dialog текущий диалог
     * @param userMessage текст сообщения пользователя
     * @param config конфигурация стратегии
     * @return результат действия стратегии
     */
    suspend fun processUserMessage(
        dialog: Dialog,
        userMessage: String,
        config: ContextManagementConfig
    ): StrategyActionResult

    /**
     * Подготавливает контекст для отправки в LLM.
     *
     * Формирует список сообщений на основе текущего состояния диалога
     * и внутренней логики стратегии.
     *
     * @param dialog текущий диалог
     * @param systemPrompt базовый system prompt
     * @param config конфигурация стратегии
     * @return подготовленный контекст для LLM
     */
    suspend fun prepareContext(
        dialog: Dialog,
        systemPrompt: String,
        config: ContextManagementConfig
    ): PreparedContext
}

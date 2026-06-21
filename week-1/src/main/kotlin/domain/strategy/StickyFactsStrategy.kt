package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.FactsStore
import io.averkhogliad.ai.challenge.week1.domain.model.StickyFact
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Стратегия Sticky Facts (Key-Value Memory).
 *
 * Автоматически извлекает ключевые факты из каждого сообщения пользователя
 * и сохраняет их в формате ключ-значение. При подготовке контекста для LLM
 * включает извлечённые факты плюс последние N сообщений.
 *
 * ## Управление состоянием
 * Состояние стратегии передаётся через параметр [state] в методах
 * [processUserMessage] и [prepareContext]. Если `state == null`, используется
 * внутреннее состояние для обратной совместимости.
 * Обновлённое состояние всегда возвращается в метаданных результата под ключом
 * [StrategyMetadataKeys.STRATEGY_STATE].
 *
 * ## Принцип работы
 * 1. При обработке сообщения: вызывает [FactsExtractor] для извлечения фактов
 *    с обработкой ошибок (graceful degradation)
 * 2. Обновляет [FactsStore] новыми фактами
 * 3. При подготовке контекста: формирует system prompt с фактами + последние N сообщений
 *
 * ## Преимущества
 * - Сохранение критически важных деталей
 * - Компактное представление контекста
 * - Хорошее удержание ключевой информации
 *
 * ## Недостатки
 * - Дополнительные вызовы LLM для извлечения фактов
 * - Возможна потеря контекста при некорректном извлечении
 *
 * @property factsExtractor экстрактор фактов для извлечения через LLM
 */
class StickyFactsStrategy(
    private val factsExtractor: FactsExtractor,
    private val extractionTimeoutMs: Long = 30_000L
) : ContextManagementStrategy {

    override val name: String = "Sticky Facts"
    override val description: String = "Автоматически извлекает и хранит ключевые факты. " +
            "Лучше для длинных диалогов с важными деталями."

    // Внутреннее состояние — сохранено для обратной совместимости.
    private var factsStore = FactsStore()

    // ═══════════════════════════════════════════════════════════════
    // processUserMessage
    // ═══════════════════════════════════════════════════════════════

    override suspend fun processUserMessage(
        dialog: Dialog,
        userMessage: String,
        config: ContextManagementConfig,
        state: StrategyState?
    ): StrategyActionResult {
        require(userMessage.isNotBlank()) { "userMessage cannot be blank" }

        val stickyConfig = config.stickyFacts
        val currentState = extractOrCreateState(state)

        // Извлекаем факты с таймаутом и обработкой ошибок (graceful degradation)
        val extractedFacts = withTimeoutOrNull(extractionTimeoutMs) {
            try {
                factsExtractor.extractFacts(
                    userMessage = userMessage,
                    messageIndex = dialog.messages.size,
                    extractionModelId = stickyConfig.extractionModelId
                )
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()

        val updatedFactsStore = if (extractedFacts.isNotEmpty()) {
            currentState.factsStore.addAll(extractedFacts)
        } else {
            currentState.factsStore
        }

        val updatedState = currentState.copy(factsStore = updatedFactsStore)

        // Синхронизируем внутреннее состояние для обратной совместимости
        syncInternalState(updatedState)

        return StrategyActionResult(
            actionsPerformed = listOf(
                StrategyAction.FactsExtracted(extractedFacts.size)
            ),
            metadata = mapOf(
                StrategyMetadataKeys.EXTRACTED_FACTS to extractedFacts,
                StrategyMetadataKeys.FACTS_COUNT to updatedState.factsStore.facts.size,
                StrategyMetadataKeys.STRATEGY_STATE to updatedState
            )
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // prepareContext
    // ═══════════════════════════════════════════════════════════════

    override suspend fun prepareContext(
        dialog: Dialog,
        systemPrompt: String,
        config: ContextManagementConfig,
        state: StrategyState?
    ): PreparedContext {
        val stickyConfig = config.stickyFacts
        val currentState = extractOrCreateState(state)

        val messages = mutableListOf<ChatMessage>()

        // System prompt с фактами
        val factsText = currentState.factsStore.formatForContext()
        val enhancedSystemPrompt = if (factsText.isNotEmpty()) {
            "$systemPrompt\n\n$factsText"
        } else {
            systemPrompt
        }
        messages.add(ChatMessage.system(enhancedSystemPrompt))

        // Последние N сообщений (windowSize)
        val recentMessages = dialog.messages.takeLast(stickyConfig.windowSize)
        messages.addAll(recentMessages)

        return PreparedContext.fromMessages(
            messages = messages,
            metadata = mapOf(
                StrategyMetadataKeys.STRATEGY to "sticky-facts",
                StrategyMetadataKeys.WINDOW_SIZE to stickyConfig.windowSize,
                StrategyMetadataKeys.FACTS_COUNT to currentState.factsStore.facts.size,
                StrategyMetadataKeys.FACTS to currentState.factsStore.facts,
                StrategyMetadataKeys.STRATEGY_STATE to currentState
            )
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // Публичные методы — работа с состоянием
    // ═══════════════════════════════════════════════════════════════

    /**
     * Получает текущее хранилище фактов.
     *
     * @param state текущее состояние стратегии (опционально; если null — используется внутреннее)
     */
    fun getFactsStore(state: StrategyState.StickyFactsState? = null): FactsStore {
        return state?.factsStore ?: factsStore
    }

    /**
     * Очищает хранилище фактов.
     *
     * @param state текущее состояние стратегии (опционально; если null — используется внутреннее)
     */
    fun clearFacts(state: StrategyState.StickyFactsState? = null) {
        val emptyStore = FactsStore()
        if (state != null) {
            // Не можем мутировать state (он data class), обновляем только внутреннее
            factsStore = emptyStore
        } else {
            factsStore = emptyStore
        }
    }

    /**
     * Добавляет факт вручную.
     *
     * @param state текущее состояние стратегии (опционально; если null — используется внутреннее)
     */
    fun addFact(
        key: String,
        value: String,
        category: io.averkhogliad.ai.challenge.week1.domain.model.FactCategory,
        state: StrategyState.StickyFactsState? = null
    ) {
        val fact = StickyFact(
            key = StickyFact.createKey(category, key),
            value = value,
            category = category
        )
        val updated = (state?.factsStore ?: factsStore).addOrUpdate(fact)
        if (state == null) {
            factsStore = updated
        }
    }

    /**
     * Удаляет факт по ключу.
     *
     * @param state текущее состояние стратегии (опционально; если null — используется внутреннее)
     */
    fun removeFact(key: String, state: StrategyState.StickyFactsState? = null) {
        val updated = (state?.factsStore ?: factsStore).remove(key)
        if (state == null) {
            factsStore = updated
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Вспомогательные методы
    // ═══════════════════════════════════════════════════════════════

    /**
     * Извлекает или создаёт состояние стратегии.
     */
    private fun extractOrCreateState(
        state: StrategyState?
    ): StrategyState.StickyFactsState {
        return (state as? StrategyState.StickyFactsState)
            ?: StrategyState.StickyFactsState.createInitial()
    }

    /**
     * Синхронизирует внутреннее mutable-состояние с переданным (для обратной совместимости).
     */
    private fun syncInternalState(state: StrategyState.StickyFactsState) {
        factsStore = state.factsStore
    }
}

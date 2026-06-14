package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.FactsStore
import io.averkhogliad.ai.challenge.week1.domain.model.StickyFact
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage

/**
 * Стратегия Sticky Facts (Key-Value Memory).
 *
 * Автоматически извлекает ключевые факты из каждого сообщения пользователя
 * и сохраняет их в формате ключ-значение. При подготовке контекста для LLM
 * включает извлечённые факты плюс последние N сообщений.
 *
 * ## Принцип работы
 * 1. При обработке сообщения: вызывает [FactsExtractor] для извлечения фактов
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
    private val factsExtractor: FactsExtractor
) : ContextManagementStrategy {

    override val name: String = "Sticky Facts"
    override val description: String = "Автоматически извлекает и хранит ключевые факты. " +
            "Лучше для длинных диалогов с важными деталями."

    // Хранилище фактов для текущей сессии
    private var factsStore = FactsStore()

    override suspend fun processUserMessage(
        dialog: Dialog,
        userMessage: String,
        config: ContextManagementConfig
    ): StrategyActionResult {
        val stickyConfig = config.stickyFacts
        val messageIndex = dialog.messages.size

        // Извлекаем факты из сообщения
        val extractedFacts = factsExtractor.extractFacts(
            userMessage = userMessage,
            messageIndex = messageIndex,
            extractionModelId = stickyConfig.extractionModelId
        )

        // Обновляем хранилище фактов
        if (extractedFacts.isNotEmpty()) {
            factsStore = factsStore.addAll(extractedFacts)
        }

        return StrategyActionResult(
            actionsPerformed = listOf(
                StrategyAction.FactsExtracted(extractedFacts.size)
            ),
            metadata = mapOf(
                "extractedFacts" to extractedFacts,
                "totalFacts" to factsStore.facts.size
            )
        )
    }

    override suspend fun prepareContext(
        dialog: Dialog,
        systemPrompt: String,
        config: ContextManagementConfig
    ): PreparedContext {
        val stickyConfig = config.stickyFacts

        // Формируем список сообщений для контекста
        val messages = mutableListOf<ChatMessage>()

        // 1. System prompt с фактами
        val factsText = factsStore.formatForContext()
        val enhancedSystemPrompt = if (factsText.isNotEmpty()) {
            "$systemPrompt\n\n$factsText"
        } else {
            systemPrompt
        }
        messages.add(ChatMessage.system(enhancedSystemPrompt))

        // 2. Последние N сообщений (windowSize)
        val recentMessages = dialog.messages.takeLast(stickyConfig.windowSize)
        messages.addAll(recentMessages)

        return PreparedContext.fromMessages(
            messages = messages,
            metadata = mapOf(
                "strategy" to "sticky-facts",
                "windowSize" to stickyConfig.windowSize,
                "factsCount" to factsStore.facts.size,
                "facts" to factsStore.facts
            )
        )
    }

    /**
     * Получает текущее хранилище фактов.
     */
    fun getFactsStore(): FactsStore = factsStore

    /**
     * Очищает хранилище фактов.
     */
    fun clearFacts() {
        factsStore = FactsStore()
    }

    /**
     * Добавляет факт вручную.
     */
    fun addFact(key: String, value: String, category: io.averkhogliad.ai.challenge.week1.domain.model.FactCategory) {
        val fact = StickyFact(
            key = StickyFact.createKey(category, key),
            value = value,
            category = category
        )
        factsStore = factsStore.addOrUpdate(fact)
    }

    /**
     * Удаляет факт по ключу.
     */
    fun removeFact(key: String) {
        factsStore = factsStore.remove(key)
    }
}

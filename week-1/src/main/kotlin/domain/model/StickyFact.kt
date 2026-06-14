package io.averkhogliad.ai.challenge.week1.domain.model

import java.time.Instant

/**
 * Категория факта в стратегии Sticky Facts.
 */
enum class FactCategory(val code: String) {
    GOAL("goal"),
    CONSTRAINT("constraint"),
    PREFERENCE("preference"),
    AGREEMENT("agreement"),
    REQUIREMENT("requirement");

    companion object {
        fun fromCode(code: String): FactCategory =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown fact category: $code")
    }
}

/**
 * Факт, извлечённый из диалога (Key-Value Memory).
 *
 * @property key уникальный ключ факта (формат: "category:name")
 * @property value значение факта
 * @property category категория факта
 * @property extractedAt время извлечения факта
 * @property sourceMessageIndex индекс сообщения, из которого извлечён факт
 */
data class StickyFact(
    val key: String,
    val value: String,
    val category: FactCategory,
    val extractedAt: Instant = Instant.now(),
    val sourceMessageIndex: Int = -1
) {
    init {
        require(key.isNotBlank()) { "Fact key cannot be blank" }
        require(value.isNotBlank()) { "Fact value cannot be blank" }
    }

    /**
     * Создаёт ключ из категории и имени.
     */
    companion object {
        fun createKey(category: FactCategory, name: String): String =
            "${category.code}:$name"

        fun parseKey(key: String): Pair<FactCategory, String> {
            val parts = key.split(":", limit = 2)
            require(parts.size == 2) { "Invalid fact key format: $key (expected 'category:name')" }
            return FactCategory.fromCode(parts[0]) to parts[1]
        }
    }
}

/**
 * Хранилище фактов для стратегии Sticky Facts.
 *
 * @property facts карта фактов (ключ -> факт)
 * @property maxFacts максимальное количество хранимых фактов
 */
data class FactsStore(
    val facts: Map<String, StickyFact> = emptyMap(),
    val maxFacts: Int = 20
) {
    init {
        require(maxFacts > 0) { "maxFacts must be positive, got $maxFacts" }
    }

    /**
     * Добавляет или обновляет факт.
     * Если превышен лимит, удаляет самые старые факты.
     */
    fun addOrUpdate(fact: StickyFact): FactsStore {
        val newFacts = facts.toMutableMap()
        newFacts[fact.key] = fact

        // Если превышен лимит, удаляем самые старые факты
        if (newFacts.size > maxFacts) {
            val sortedByTime = newFacts.values.sortedBy { it.extractedAt }
            val toRemove = sortedByTime.take(newFacts.size - maxFacts)
            toRemove.forEach { newFacts.remove(it.key) }
        }

        return copy(facts = newFacts)
    }

    /**
     * Добавляет несколько фактов.
     */
    fun addAll(newFacts: List<StickyFact>): FactsStore {
        var store = this
        newFacts.forEach { store = store.addOrUpdate(it) }
        return store
    }

    /**
     * Удаляет факт по ключу.
     */
    fun remove(key: String): FactsStore =
        copy(facts = facts - key)

    /**
     * Очищает все факты.
     */
    fun clear(): FactsStore = copy(facts = emptyMap())

    /**
     * Получает факты по категории.
     */
    fun getByCategory(category: FactCategory): List<StickyFact> =
        facts.values.filter { it.category == category }

    /**
     * Форматирует факты для включения в контекст LLM.
     */
    fun formatForContext(): String {
        if (facts.isEmpty()) return ""

        return buildString {
            appendLine("[Key facts from conversation]")
            facts.values
                .sortedBy { it.category }
                .forEach { fact ->
                    appendLine("- [${fact.category.code}] ${fact.key.substringAfter(":")}: ${fact.value}")
                }
        }
    }
}

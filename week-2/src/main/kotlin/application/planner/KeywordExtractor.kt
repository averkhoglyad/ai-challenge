package io.averkhogliad.ai.challenge.week2.application.planner

/**
 * Извлекает ключевые слова из названия и описания задачи
 * для последующего поиска релевантных фактов в LTM.
 *
 * ## Архитектурная роль
 * - **Application Layer** — специализированный компонент-стратегия
 * - **Single Responsibility** — только извлечение ключевых слов
 * - **Stateless** — не хранит состояние, полностью testable
 *
 * ## Использование
 * ```kotlin
 * val extractor = KeywordExtractor()
 * val keywords = extractor.extract("Добавить кэширование Redis", "Нужно добавить Redis кэш...")
 * // -> ["redis", "кэширование", "нужно", "кэш"]
 * ```
 */
class KeywordExtractor {

    /**
     * Стоп-слова, которые исключаются из результата.
     * Включает как русские, так и английские служебные слова,
     * а также глаголы-действия (implement, create, add и т.д.).
     */
    private val stopWords: Set<String> = setOf(
        "и", "в", "на", "с", "по", "для", "от", "до", "за", "над",
        "the", "a", "an", "in", "on", "at", "to", "for", "of", "with",
        "and", "or", "but", "is", "are", "was", "were", "be", "been",
        "implement", "create", "add", "make", "build", "write", "update"
    )

    /**
     * Извлекает ключевые слова из названия и описания задачи.
     *
     * Алгоритм:
     * 1. Разбивает текст на слова по разделителям (пробелы, пунктуация)
     * 2. Фильтрует: длина > 3 символов, не стоп-слово
     * 3. Убирает дубликаты
     * 4. Ограничивает результат до [maxKeywords] слов
     *
     * @param title название задачи
     * @param description описание задачи (может быть null)
     * @param maxKeywords максимальное количество ключевых слов (по умолчанию 5)
     * @return список уникальных ключевых слов в нижнем регистре
     */
    fun extract(title: String, description: String?, maxKeywords: Int = 5): List<String> {
        val words = mutableListOf<String>()

        words.addAll(extractFromText(title))
        if (description != null) {
            words.addAll(extractFromText(description))
        }

        return words.distinct().take(maxKeywords)
    }

    /**
     * Извлекает слова из одного текстового блока.
     */
    private fun extractFromText(text: String): List<String> {
        return text.split(Regex("[\\s,.;:!?]+"))
            .map { it.lowercase() }
            .filter { it.length > 3 && it !in stopWords }
    }
}

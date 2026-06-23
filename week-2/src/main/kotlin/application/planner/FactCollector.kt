package io.averkhogliad.ai.challenge.week2.application.planner

import io.averkhogliad.ai.challenge.week2.domain.model.Fact
import io.averkhogliad.ai.challenge.week2.domain.service.FactRepository

/**
 * Собирает релевантные факты из LTM на основе ключевых слов задачи.
 * Использует batch-поиск ([FactRepository.searchBatch]) для устранения N+1 проблемы.
 *
 * ## Архитектурная роль
 * - **Application Layer** — специализированный компонент-стратегия
 * - **Single Responsibility** — только сбор фактов по ключевым словам
 * - **Testable** — зависит только от интерфейса [FactRepository]
 *
 * ## N+1 устранение
 * Вместо последовательного вызова [FactRepository.search] для каждого ключевого слова,
 * используется [FactRepository.searchBatch] — один SQL-запрос с OR-условиями.
 *
 * ## Использование
 * ```kotlin
 * val collector = FactCollector(factRepository, KeywordExtractor())
 * val facts = collector.collect("Добавить кэширование Redis", "Нужно добавить Redis кэш...")
 * ```
 */
class FactCollector(
    private val factRepository: FactRepository,
    private val keywordExtractor: KeywordExtractor = KeywordExtractor()
) {

    /**
     * Собирает релевантные факты по названию и описанию задачи.
     *
     * Алгоритм:
     * 1. Извлекает ключевые слова через [KeywordExtractor]
     * 2. Если ключевых слов нет — возвращает до 5 последних фактов
     * 3. Выполняет batch-поиск ([FactRepository.searchBatch]) по всем ключевым словам
     * 4. Ограничивает результат до [maxFacts] (по умолчанию 10)
     *
     * @param taskTitle название задачи
     * @param taskDescription описание задачи (может быть null)
     * @param maxFacts максимальное количество возвращаемых фактов
     * @return список релевантных фактов
     */
    suspend fun collect(taskTitle: String, taskDescription: String?, maxFacts: Int = 10): List<Fact> {
        val keywords = keywordExtractor.extract(taskTitle, taskDescription)

        if (keywords.isEmpty()) {
            return factRepository.findAll().take(5)
        }

        val facts = factRepository.searchBatch(keywords)
        return facts.take(maxFacts)
    }
}

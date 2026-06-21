package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import io.averkhogliad.ai.challenge.week1.domain.service.ChatRole

/**
 * Детектор смены темы диалога на основе анализа ключевых слов.
 *
 * Вынесен из [BranchingStrategy] в рамках рефакторинга (Этап 2) для:
 * - Разделения ответственности (Single Responsibility Principle)
 * - Возможности независимого тестирования
 * - Повторного использования в других стратегиях
 *
 * ## Алгоритм
 * 1. Извлекает значимые ключевые слова из N последних сообщений пользователя
 * 2. Извлекает ключевые слова из нового сообщения
 * 3. Вычисляет коэффициент Жаккара (Jaccard similarity) между множествами
 * 4. Если доля пересечения ниже порога [sensitivity] — тема считается изменившейся
 *
 * ## Извлечение ключевых слов
 * - Приведение к нижнему регистру
 * - Удаление стоп-слов (русских и английских)
 * - Фильтрация коротких токенов (< 2 символов)
 *
 * @property defaultSensitivity порог чувствительности по умолчанию (0.0–1.0). Чем ниже, тем легче срабатывает.
 * @property defaultContextSize количество последних сообщений для анализа по умолчанию
 */
class TopicChangeDetector(
    private val defaultSensitivity: Double = 0.3,
    private val defaultContextSize: Int = 3
) {

    /**
     * Определяет, изменилась ли тема диалога.
     *
     * Анализирует только сообщения с ролью [ChatRole.USER].
     *
     * @param userMessage новое сообщение пользователя
     * @param recentMessages последние N сообщений в диалоге/ветке
     * @param sensitivity порог чувствительности (0.0–1.0). Чем ниже — тем легче срабатывает детекция.
     * @param contextSize сколько последних сообщений пользователя анализировать
     * @return `true` если тема изменилась (пересечение ключевых слов ниже порога)
     */
    fun detectTopicChange(
        userMessage: String,
        recentMessages: List<ChatMessage>,
        sensitivity: Double = defaultSensitivity,
        contextSize: Int = defaultContextSize
    ): Boolean {
        // Берём последние contextSize сообщений пользователя (не system/assistant)
        val recentUserMessages = recentMessages
            .filter { it.role == ChatRole.USER }
            .takeLast(contextSize)
            .map { it.content }

        if (recentUserMessages.isEmpty()) return false

        // Извлекаем ключевые слова из истории
        val recentKeywords = recentUserMessages
            .flatMap { extractKeywords(it) }
            .toSet()

        // Извлекаем ключевые слова из нового сообщения
        val newKeywords = extractKeywords(userMessage).toSet()

        if (recentKeywords.isEmpty() || newKeywords.isEmpty()) return false

        // Вычисляем коэффициент Жаккара (Jaccard similarity)
        val intersection = recentKeywords.intersect(newKeywords).size
        val union = recentKeywords.union(newKeywords).size
        val jaccard = intersection.toDouble() / union.toDouble()

        // Смена темы = низкое пересечение
        return jaccard < sensitivity
    }

    /**
     * Извлекает значимые ключевые слова из текста сообщения.
     *
     * Обработка:
     * - Приведение к нижнему регистру
     * - Удаление пунктуации (остаются только буквы, цифры, пробелы)
     * - Разбиение на токены по пробелам
     * - Фильтрация стоп-слов (русские и английские)
     * - Фильтрация коротких токенов (< 2 символов)
     *
     * @param text исходный текст сообщения
     * @return список значимых ключевых слов в порядке появления
     */
    fun extractKeywords(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-zа-яё0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in STOP_WORDS }
    }

    companion object {
        /**
         * Множество стоп-слов (русские и английские).
         *
         * Содержит предлоги, союзы, местоимения, артикли, вспомогательные глаголы
         * и другие высокочастотные слова, не несущие тематической нагрузки.
         */
        private val STOP_WORDS: Set<String> = setOf(
            // Русские стоп-слова
            "и", "в", "на", "с", "по", "к", "из", "от", "для", "не", "то", "что",
            "как", "это", "так", "а", "но", "о", "же", "за", "бы", "у", "до",
            "да", "нет", "или", "если", "мы", "вы", "ты", "он", "она", "они",
            "мне", "меня", "его", "её", "им", "их", "вам", "вас", "тебе", "тебя",
            "всё", "все", "ещё", "уже", "там", "тут", "где", "кто", "когда",
            "можно", "надо", "нужно", "очень", "более", "также", "только",
            "который", "которая", "которое", "которые", "быть", "будет",
            "есть", "был", "была", "было", "были", "чем", "того", "этом", "этого",
            "под", "над", "при", "без", "через", "перед", "между", "около",
            // Английские стоп-слова
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "can", "shall", "to", "of", "in", "for",
            "on", "with", "at", "by", "from", "as", "into", "through", "during",
            "before", "after", "above", "below", "between", "out", "off", "over",
            "under", "again", "further", "then", "once", "here", "there", "when",
            "where", "why", "how", "all", "both", "each", "few", "more", "most",
            "other", "some", "such", "no", "nor", "not", "only", "own", "same",
            "so", "than", "too", "very", "just", "because", "but", "and", "or",
            "if", "while", "it", "its", "my", "your", "his", "her", "our", "their",
            "me", "him", "us", "them", "this", "that", "these", "those", "what",
            "which", "who", "whom", "about", "up", "down", "any", "let", "need",
            "now", "also", "much", "well", "still", "new"
        )
    }
}

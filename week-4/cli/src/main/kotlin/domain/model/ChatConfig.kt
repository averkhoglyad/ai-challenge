package io.averkhogliad.ai.challenge.week4.cli.domain.model

/**
 * Конфигурация чат-сессии.
 *
 * ## Архитектурная роль
 * - **Value Object** — неизменяемые параметры настройки агрегата [ChatSession]
 *
 * ## Свойства
 * - [historyWindowSize] — размер окна истории сообщений, отправляемых в LLM (по умолчанию 6)
 * - [nameMaxLength] — максимальная длина автоимени чата (по умолчанию 50)
 * - [autoNameEnabled] — включена ли автоматическая генерация имени чата
 * - [taskStateExtractionEnabled] — включено ли автоматическое извлечение памяти задачи
 * - [taskStateMaxTerms] — максимальное количество терминов в памяти задачи
 * - [taskStateMaxConstraints] — максимальное количество ограничений в памяти задачи
 */
data class ChatConfig(
    val historyWindowSize: Int = 6,
    val nameMaxLength: Int = 50,
    val autoNameEnabled: Boolean = true,
    val taskStateExtractionEnabled: Boolean = true,
    val taskStateMaxTerms: Int = 50,
    val taskStateMaxConstraints: Int = 50,
    val maxClarifiedFacts: Int = 50
) {
    init {
        require(historyWindowSize > 0) { "historyWindowSize must be positive, got $historyWindowSize" }
        require(nameMaxLength > 0) { "nameMaxLength must be positive, got $nameMaxLength" }
        require(taskStateMaxTerms > 0) { "taskStateMaxTerms must be positive, got $taskStateMaxTerms" }
        require(taskStateMaxConstraints > 0) { "taskStateMaxConstraints must be positive, got $taskStateMaxConstraints" }
        require(maxClarifiedFacts > 0) { "maxClarifiedFacts must be positive, got $maxClarifiedFacts" }
    }
}

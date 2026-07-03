package io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model

/**
 * Статус выполнения индексации.
 */
enum class RunStatus {
    /** Индексация в процессе выполнения */
    RUNNING,

    /** Индексация успешно завершена */
    COMPLETED,

    /** Индексация завершилась с ошибкой */
    FAILED
}

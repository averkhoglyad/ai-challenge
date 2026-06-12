package io.averkhogliad.ai.challenge.week1.domain.config

/**
 * Immutable корневая конфигурация приложения.
 *
 * Агрегирует все конфигурационные объекты приложения в единую структуру.
 * Является точкой входа для получения конфигурации доменного слоя.
 *
 * Зависит только от domain-конфигов ([LlmConfig], [TaskExecutionConfig]).
 * Не зависит от [io.averkhogliad.ai.challenge.utils.config.Config],
 * [io.averkhogliad.ai.challenge.utils.config.ConfigProvider] и application.properties.
 *
 * @property llm Конфигурация LLM-клиента
 * @property defaultExecution Конфигурация выполнения задачи по умолчанию
 * @property replTimeoutSeconds Таймаут REPL-сессии в секундах (> 0)
 */
data class AppConfig(
    val llm: LlmConfig,
    val defaultExecution: TaskExecutionConfig = TaskExecutionConfig(),
    val replTimeoutSeconds: Long = 300
) {
    init {
        require(replTimeoutSeconds > 0) { "replTimeoutSeconds must be positive, got $replTimeoutSeconds" }
    }
}

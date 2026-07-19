package io.averkhogliad.ai.challenge.week4.cli.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.config.AppConfig

/**
 * Порт для получения конфигурации приложения.
 *
 * Определяет контракт, который domain-слой использует для получения
 * конфигурационных данных. Реализация в infrastructure-слое ([ConfigAdapter])
 * читает данные из [Config][io.averkhogliad.ai.challenge.llm.config.Config]
 * и маппит их в domain-конфиги.
 *
 * Domain-слой НЕ зависит от:
 * - [Config][io.averkhogliad.ai.challenge.llm.config.Config]
 * - [ConfigProvider][io.averkhogliad.ai.challenge.llm.config.ConfigProvider]
 * - application.properties
 */
interface ConfigPort {

    /**
     * Загружает корневую конфигурацию приложения из infrastructure-слоя.
     *
     * @return [AppConfig] с заполненными [LlmConfig][io.averkhogliad.ai.challenge.week4.cli.domain.config.LlmConfig]
     *         и [TaskExecutionConfig][io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig]
     * @throws NoSuchElementException если отсутствуют обязательные ключи (api.base-url, api.key, api.model, etc.)
     * @throws IllegalArgumentException если значения не проходят валидацию domain-конфигов
     */
    fun loadAppConfig(): AppConfig
}

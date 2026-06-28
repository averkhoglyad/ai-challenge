package io.averkhogliad.ai.challenge.week3.cli.domain.config

/**
 * Конфигурация внешних сервисов для Task3.
 *
 * @property eventsBaseUrl базовый URL Events-сервиса
 * @property notificationsBaseUrl базовый URL Notifications-сервиса
 * @property connectTimeoutSeconds таймаут соединения в секундах
 * @property readTimeoutSeconds таймаут чтения в секундах
 */
data class ServicesConfig(
    val eventsBaseUrl: String,
    val notificationsBaseUrl: String,
    val connectTimeoutSeconds: Int = 3,
    val readTimeoutSeconds: Int = 10
)

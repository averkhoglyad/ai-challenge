package io.averkhogliad.ai.challenge.utils.config

/**
 * Минимальная реализация [Config] на основе Map.
 * Используется как общий test-fixture для всех тестов в проекте.
 *
 * ```kotlin
 * val config = TestConfig(mapOf(
 *     "api.base-url" to "https://api.example.com",
 *     "api.key" to "test-key"
 * ))
 * ```
 */
class TestConfig(private val properties: Map<String, String>) : Config {
    override fun get(key: String): String = properties[key] ?: throw NoSuchElementException("Key not found: $key")
    override fun getOrNull(key: String): String? = properties[key]
    override fun keys(): Set<String> = properties.keys

    companion object {
        /**
         * Создаёт конфигурацию с минимальным набором обязательных ключей для API.
         */
        fun apiDefaults(
            baseUrl: String = "https://api.example.com",
            key: String = "test-key",
            model: String = "test-model"
        ): TestConfig =
            TestConfig(
                mapOf(
                    "api.base-url" to baseUrl,
                    "api.key" to key,
                    "api.model" to model,
                    "api.connect-timeout" to "PT10S",
                    "api.request-timeout" to "PT30S"
                )
            )
    }
}

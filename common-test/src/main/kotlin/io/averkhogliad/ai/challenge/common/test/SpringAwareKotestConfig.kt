package io.averkhogliad.ai.challenge.common.test

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.spring.SpringAutowireConstructorExtension
import io.kotest.extensions.spring.SpringExtension

/**
 * Kotest configuration with Spring extensions enabled.
 * 
 * This configuration allows Kotest to work seamlessly with Spring Boot:
 * - SpringExtension: manages Spring context lifecycle
 * - SpringAutowireConstructorExtension: enables constructor injection in test classes
 * 
 * Usage:
 * ```kotlin
 * @SpringBootTest
 * class MyTest(private val service: MyService) : FreeSpec({ ... })
 * ```
 */
class SpringAwareKotestConfig : AbstractProjectConfig() {
    override fun extensions() = listOf(SpringExtension, SpringAutowireConstructorExtension)
}

package io.averkhogliad.ai.challenge.common.test

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.spring.SpringAutowireConstructorExtension
import io.kotest.extensions.spring.SpringExtension

/**
 * Базовая конфигурация Kotest для Spring-модулей проекта.
 *
 * Подключает расширения, которые нужны тестам в `week-3` и другим Spring Boot
 * модулям, использующим `:common:test`:
 * - [SpringExtension] управляет жизненным циклом Spring Context;
 * - [SpringAutowireConstructorExtension] включает конструкторную инъекцию в тестах.
 *
 * Обычно класс указывается в `kotest.properties` как project config.
 */
class SpringAwareKotestConfig : AbstractProjectConfig() {
    override fun extensions() = listOf(SpringExtension, SpringAutowireConstructorExtension)
}

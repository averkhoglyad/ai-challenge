plugins {
    // Общая JVM-конфигурация для Kotlin-модулей проекта.
    id("buildsrc.convention.kotlin-jvm")
    // Сериализация нужна для моделей LLM, конфига и тестовых фикстур.
    alias(libs.plugins.kotlinPluginSerialization)
    // Экспортируем общие тестовые фикстуры для потребителей модуля.
    `java-test-fixtures`
}

dependencies {
    // Базовый стек: coroutines + kotlinx serialization.
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.kotlinx.coroutines.jdk8)

    testImplementation(kotlin("test"))

    // Нужен testFixtures: например, MockLlmClient использует JsonObject.
    testFixturesImplementation(libs.bundles.kotlinxEcosystem)
}

plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    alias(libs.plugins.kotlinPluginSerialization)
    // Enable test fixtures for sharing test utilities across modules.
    `java-test-fixtures`
}

dependencies {
    // Apply the kotlinx bundle of dependencies from the version catalog (`gradle/libs.versions.toml`).
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.kotlinx.coroutines.jdk8)
    testImplementation(kotlin("test"))
    // kotlinx-serialization-json needed by testFixtures (MockLlmClient uses JsonObject)
    testFixturesImplementation(libs.bundles.kotlinxEcosystem)
}

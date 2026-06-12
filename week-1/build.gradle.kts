plugins {
    // Apply the shared build logic from a convention plugin.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application

    // kotlinx.serialization for JSON encoding/decoding.
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // JSON + coroutines for Task1.
    implementation(libs.bundles.kotlinxEcosystem)

    // Mordant for rich console UI (colors, tables, progress bars, interactive menus).
    implementation(libs.mordant)

    // Shared utilities and config component.
    implementation(project(":utils"))

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":utils")))
}

application {
    // Define the Fully Qualified Name for the application main class.
    // (Kotlin compiles `App.kt` to a class with FQN `io.averkhogliad.ai.challenge.week1.AppKt`.)
    mainClass = "io.averkhogliad.ai.challenge.week1.AppKt"
}

// Настройки JVM для корректной работы с UTF-8 и JNA
tasks.withType<JavaExec>().configureEach {
    jvmArgs(
        // UTF-8 для корректного вывода кириллицы в консоли Windows
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        // Разрешаем JNA (используется Mordant) доступ к нативным функциям в Java 21+
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    )
}

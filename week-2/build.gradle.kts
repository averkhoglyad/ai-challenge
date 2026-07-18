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
    implementation(project(":common:config"))
    implementation(project(":common:llm"))

    // SQLite JDBC driver for dialog persistence.
    implementation(libs.sqliteJdbc)

    // Testing — common:test (Kotest, MockK, coroutines-test, Spring extensions)
    testImplementation(project(":common:test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.test {
    useJUnitPlatform {
        filter { excludeTestsMatching("*IT") }
    }
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    useJUnitPlatform {
        filter { includeTestsMatching("*IT") }
    }
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn("integrationTest")
}

application {
    // Define the Fully Qualified Name for the application main class.
    mainClass = "io.averkhogliad.ai.challenge.week2.AppKt"
}

// JVM settings for proper UTF-8 and JNA support
tasks.withType<JavaExec>().configureEach {
    jvmArgs(
        // UTF-8 for proper Cyrillic output in Windows console
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        // Allow JNA (used by Mordant) to access native functions on Java 21+
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    )
}

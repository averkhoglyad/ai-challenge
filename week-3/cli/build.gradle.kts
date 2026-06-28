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
    implementation(project(":common-core"))

    // SQLite JDBC driver for dialog persistence.
    implementation(libs.sqliteJdbc)

    // MCP SDK client for model context protocol integration.
    implementation(libs.mcpSdkClient)

    // Ktor CIO engine (required by MCP SDK).
    implementation(libs.ktorClientCio)

    // Ktor Content Negotiation + kotlinx.serialization JSON (REST clients for Task3).
    implementation(libs.ktorClientContentNegotiation)
    implementation(libs.ktorSerializationKotlinxJson)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":common-core")))

    // Kotest (FreeSpec style)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)

    // MockK
    testImplementation(libs.mockk)
}

application {
    // Define the Fully Qualified Name for the application main class.
    mainClass = "io.averkhogliad.ai.challenge.week3.cli.AppKt"
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

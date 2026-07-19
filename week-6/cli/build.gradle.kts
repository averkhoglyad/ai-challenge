plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // Kotlin ecosystem
    implementation(libs.bundles.kotlinxEcosystem)

    // Common modules
    implementation(project(":common:indexer"))
    implementation(project(":common:repl"))
    implementation(project(":common:repl-mordant"))
    implementation(project(":common:config"))
    implementation(project(":common:llm"))

    // Mordant for CLI
    implementation(libs.mordant)
    implementation(libs.mordantMarkdown)

    // Exposed + SQLite
    implementation(libs.exposedCore)
    implementation(libs.exposedJdbc)
    implementation(libs.sqliteJdbc)

    // MCP SDK
    implementation(libs.mcpSdkClient)
    implementation(libs.ktorClientCio)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(project(":common:test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform {
        filter {
            excludeTestsMatching("*IT")
        }
    }
}

val testSourceSet = sourceSets["test"]
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform {
        filter {
            includeTestsMatching("*IT")
        }
    }
    shouldRunAfter(tasks.test)
}

application {
    mainClass = "io.averkhogliad.ai.challenge.week6.AppKt"
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    )
}

plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":common:config"))
    api(libs.bundles.kotlinxEcosystem)
    implementation(libs.kotlinx.coroutines.jdk8)

    // Ktor for embedding clients
    implementation(libs.ktorClientCore)
    implementation(libs.ktorClientCio)
    implementation(libs.ktorClientContentNegotiation)
    implementation(libs.ktorSerializationKotlinxJson)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktorClientMock)

    // Kotest
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestProperty)
}

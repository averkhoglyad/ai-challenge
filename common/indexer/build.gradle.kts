plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(libs.kotlinxCoroutines)

    // Jsoup for HTML extraction
    implementation(libs.jsoup)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)

    // Kotest
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
}

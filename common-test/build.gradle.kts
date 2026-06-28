plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    // Kotest
    api(libs.kotestRunnerJunit5)
    api(libs.kotestAssertionsCore)
    api(libs.kotestProperty)
    api(libs.kotestExtensionsSpring)

    // MockK
    api(libs.mockk)

    // Coroutines
    api(libs.kotlinx.coroutines.test)

    // Spring Test (compileOnly — version managed by consumers via Spring Boot plugin)
    compileOnly(libs.springBootStarterTest)
    compileOnly(libs.springJdbc)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

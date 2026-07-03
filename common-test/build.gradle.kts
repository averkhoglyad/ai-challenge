plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    // Общий стек тестирования, который переиспользуют
    api(libs.kotestRunnerJunit5)
    api(libs.kotestAssertionsCore)
    api(libs.kotestProperty)
    api(libs.kotestExtensionsSpring)
    api(libs.mockk)
    api(libs.kotlinx.coroutines.test)

    // Зависимости нужны утилитам модуля, но версии задаются модулями-потребителями
    compileOnly(libs.springBootStarterTest)
    compileOnly(libs.springJdbc)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

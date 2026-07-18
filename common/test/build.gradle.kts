plugins { id("buildsrc.convention.kotlin-jvm") }

dependencies {
    api(project(":common:config"))
    api(project(":common:llm"))

    api(libs.kotestRunnerJunit5)
    api(libs.kotestAssertionsCore)
    api(libs.kotestProperty)
    api(libs.kotestExtensionsSpring)
    api(libs.mockk)
    api(libs.kotlinx.coroutines.test)

    compileOnly(libs.springBootStarterTest)
    compileOnly(libs.springJdbc)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

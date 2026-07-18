plugins { id("buildsrc.convention.kotlin-jvm") }

dependencies {
    api(libs.kotlinxCoroutines)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

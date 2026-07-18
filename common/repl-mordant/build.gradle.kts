plugins { id("buildsrc.convention.kotlin-jvm") }

dependencies {
    api(project(":common:repl"))
    api(libs.mordant)
    implementation(libs.kotlinxCoroutines)
    implementation(libs.mordantMarkdown)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

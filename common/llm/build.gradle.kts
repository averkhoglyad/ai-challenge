plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":common:config"))
    api(libs.bundles.kotlinxEcosystem)
    implementation(libs.kotlinx.coroutines.jdk8)
    testImplementation(kotlin("test"))
}

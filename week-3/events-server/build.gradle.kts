import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    // Apply the shared build logic from a convention plugin.
    id("buildsrc.convention.kotlin-jvm")

    // Apply the Application plugin to add support for building an executable JVM application.
    application

    // Make Spring-annotated classes open for CGLIB proxying.
    alias(libs.plugins.kotlinPluginSpring)

    // kotlinx.serialization for JSON encoding/decoding.
    alias(libs.plugins.kotlinPluginSerialization)

    // Spring Boot + dependency management.
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyManagement)
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.kotlinxSerialization) // kotlinx-serialization auto-configured by Spring Boot

    // Spring Data JDBC + SQLite
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation(kotlin("reflect"))
    implementation(libs.sqliteJdbc)
    implementation(libs.uuidCreator)
    implementation(libs.kotlinxDatetime)

    // Spring AI MCP
    implementation(libs.springAiMcpServerWebmvc)

    // Shared utilities and config component.


    // Testing
    testImplementation(project(":common:test"))
    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }

    // Spring Boot WebMvc test support (MockMvc auto-configuration in Boot 4.x)
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
}

application {
    // Define the Fully Qualified Name for the application main class.
    mainClass = "io.averkhogliad.ai.challenge.week3.events.AppKt"
}

tasks.test {
    useJUnitPlatform()
    // Exclude integration tests from regular test run
    exclude("**/*IT.class")
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    useJUnitPlatform()
    include("**/*IT.class")
    shouldRunAfter(tasks.test)
    val testTask = tasks.test.get()
    testClassesDirs = testTask.testClassesDirs
    classpath = testTask.classpath
}

tasks.check {
    dependsOn("integrationTest")
}

tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("events-server:${project.version}")
    environment.set(mapOf("BP_JVM_VERSION" to "21"))
}

plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
    alias(libs.plugins.kotlinPluginSpring)
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyManagement)
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(kotlin("reflect"))
    implementation(libs.kotlinxSerialization)

    // Spring AI MCP Server
    implementation(libs.springAiMcpServerWebmvc)

    // Testing
    testImplementation(project(":common:test"))
    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
}

application {
    mainClass = "io.averkhogliad.ai.challenge.week6.ticketserver.TicketServerApplicationKt"
}

tasks.test {
    exclude("**/*IT.class")
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

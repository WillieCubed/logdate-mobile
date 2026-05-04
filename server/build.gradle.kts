import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ktor)
    jacoco
    application
}

group = "app.logdate.server"
version = "0.1.0"

application {
    mainClass.set("app.logdate.server.ApplicationKt")
    applicationDefaultJvmArgs =
        listOf("-Dio.ktor.development=${extra["io.ktor.development"] ?: "false"}")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Shared project dependencies
    implementation(projects.shared.model)
    implementation(projects.shared.config)
    implementation(projects.shared.atprotoCrypto)
    implementation(projects.shared.atprotoIdentity)
    implementation(projects.shared.atprotoLexicon)
    implementation(projects.shared.atprotoPds)
    implementation(projects.shared.atprotoPdsRuntime)
    implementation(projects.shared.atprotoPlc)
    implementation(projects.shared.atprotoRepo)
    implementation(projects.client.util)

    // Ktor Server (only using available libs)
    implementation(libs.logback)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.http.redirect)
    implementation(libs.ktor.swagger.ui)
    implementation(libs.ktor.openapi)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.swagger.core)
    implementation(libs.ktor.client.okhttp)

    // Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // WebAuthn
    implementation(libs.webauthn4j)

    // JWT (Kotlin Multiplatform)
    implementation(libs.jwtKt)

    // App Store Server Notifications V2 (signed JWS payload verification)
    implementation(libs.appStoreServerLibrary)

    // Logging
    implementation(libs.napier)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.postgresql)
    implementation(libs.postgres.socket.factory)
    implementation(libs.hikariCP)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    // Google Cloud Storage
    implementation(libs.google.cloud.storage)

    // Koin DI
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    // Testing
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.h2)

    // E2E Testing
    testImplementation(libs.testcontainers.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}

// Test configuration
tasks.test {
    // Disable parallel test execution due to Koin global context
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    maxParallelForks = 1

    // Testcontainers configuration
    systemProperty("testcontainers.reuse.enable", "true")
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "METHOD"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.named<Jar>("jar") {
    archiveFileName.set("logdate-server.jar")
}

tasks.named<Jar>("shadowJar") {
    archiveFileName.set("logdate-server-all.jar")
}

val openApiOutputDir = layout.buildDirectory.dir("openapi")

tasks.register<JavaExec>("generateOpenApi") {
    group = "documentation"
    description = "Generate OpenAPI JSON and YAML from the running server routes."

    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.logdate.server.OpenApiExporterKt")
    systemProperty("logdate.openapi.outputDir", openApiOutputDir.get().asFile.absolutePath)
    outputs.dir(openApiOutputDir)
}

tasks.register<JavaExec>("validateOpenApi") {
    group = "verification"
    description = "Validate generated OpenAPI artifacts and required route coverage."
    dependsOn("generateOpenApi")

    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.logdate.server.OpenApiValidatorKt")
    systemProperty("logdate.openapi.outputDir", openApiOutputDir.get().asFile.absolutePath)
    inputs.dir(openApiOutputDir)
}

tasks.named("check") {
    dependsOn("validateOpenApi")
    dependsOn("jacocoTestCoverageVerification")
}

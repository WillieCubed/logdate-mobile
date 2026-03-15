plugins {
    alias(libs.plugins.kotlin.jvm)
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
    testImplementation(projects.server)
    testImplementation(projects.client.sync)
    testImplementation(projects.shared.config)
    testImplementation(projects.shared.model)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)

    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.okhttp)
    testImplementation(libs.ktor.client.content.negotiation)
}

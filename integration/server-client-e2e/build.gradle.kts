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
    testImplementation(projects.client.networking)
    testImplementation(projects.client.data)
    testImplementation(projects.client.database)
    testImplementation(projects.client.device)
    testImplementation(projects.client.repository)
    testImplementation(projects.client.logdateDatastore)
    testImplementation(libs.sqlite.bundled)
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

// Build once, then launch the local Android fixture without holding a Gradle daemon or runner lock.
tasks.register("writeAndroidHistoryHarnessClasspath") {
    dependsOn(tasks.testClasses)
    val destination = layout.buildDirectory.file("android-history-harness.classpath")
    val harnessClasspath = sourceSets.test.get().runtimeClasspath
    inputs.files(harnessClasspath)
    outputs.file(destination)
    doLast {
        destination.get().asFile.writeText(harnessClasspath.asPath)
    }
}

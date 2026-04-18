import java.util.Properties

plugins {
    kotlin("jvm") version "2.3.20"
    application
}

val rootProperties =
    Properties().apply {
        file("../../gradle.properties").inputStream().use(::load)
    }

val atprotoVersion =
    providers.gradleProperty("atprotoVersion").orElse(rootProperties.getProperty("atproto.version") ?: "0.1.0")

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("io.ktor:ktor-client-core:3.4.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.4.2")
    implementation("io.ktor:ktor-client-mock:3.4.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.2")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")

    implementation("studio.hypertext.atproto:atproto-syntax:${atprotoVersion.get()}")
    implementation("studio.hypertext.atproto:atproto-identity:${atprotoVersion.get()}")
    implementation("studio.hypertext.atproto:atproto-repo:${atprotoVersion.get()}")
    implementation("studio.hypertext.atproto:atproto-xrpc:${atprotoVersion.get()}")
    implementation("studio.hypertext.atproto:atproto-pds:${atprotoVersion.get()}")
}

application {
    mainClass.set("studio.hypertext.atproto.sample.MainKt")
}

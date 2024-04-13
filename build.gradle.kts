// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.android.tools.build.gradle.plugin)
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.hilt.gradle.plugin)
        classpath(libs.dokka.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.logdate.android.application) apply false
    alias(libs.plugins.logdate.android.library) apply false
    alias(libs.plugins.logdate.android.test) apply false
    alias(libs.plugins.logdate.jvm.library) apply false
    alias(libs.plugins.logdate.compose) apply false
    alias(libs.plugins.logdate.dynamic) apply false
    alias(libs.plugins.logdate.documentation) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false
    alias(libs.plugins.google.gms) apply false
}
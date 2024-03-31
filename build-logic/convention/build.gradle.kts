/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    `kotlin-dsl`
}

group = "app.logdate.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.tools.build.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.firebase.perf.plugin)
}

gradlePlugin {
    /**
     * Register convention plugins so they are available in the buildlogic scripts of the application
     */
    plugins {
        register("logdateAndroidApplication") {
            id = "logdate.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("logdateAndroidLibrary") {
            id = "logdate.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("logdateAndroidTest") {
            id = "logdate.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("logdateJvmLibrary") {
            id = "logdate.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("logdateCompose") {
            id = "logdate.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("logdateDynamic") {
            id = "logdate.dynamic"
            implementationClass = "DynamicFeatureConventionPlugin"
        }
    }
}

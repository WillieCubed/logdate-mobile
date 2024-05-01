package app.logdate.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.google.firebase.perf.plugin.FirebasePerfExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

internal fun Project.configureFirebaseDeps(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        with(extensions.getByType<VersionCatalogsExtension>().named("libs")) {
            dependencies {
                "implementation"(platform(findLibrary("firebase.bom").get()))
                "implementation"(findLibrary("firebase.analytics").get())
                "implementation"(findLibrary("firebase.crashlytics").get())
                "implementation"(findLibrary("firebase.perf").get())
            }
        }
    }
}

internal fun configureFirebasePerf(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        buildTypes {
            getByName("debug") {
                configure<FirebasePerfExtension> {
                    // Set this flag to 'false' to disable @AddTrace annotation processing and
                    // automatic monitoring of HTTP/S network requests
                    // for a specific build variant at compile time.
                    setInstrumentationEnabled(false)
                }
//                withGroovyBuilder {
//                    "FirebasePerformance" {
//                        invokeMethod("setInstrumentationEnabled", false)
//                    }
//                }
            }
        }
    }
}
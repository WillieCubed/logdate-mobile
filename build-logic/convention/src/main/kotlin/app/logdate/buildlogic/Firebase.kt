package app.logdate.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.google.firebase.perf.plugin.FirebasePerfExtension
import org.gradle.kotlin.dsl.configure

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
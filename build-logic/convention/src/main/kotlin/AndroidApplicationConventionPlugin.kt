import app.logdate.buildlogic.configureAndroid
import app.logdate.buildlogic.configureAndroidApp
import app.logdate.buildlogic.configureFirebasePerf
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("dagger.hilt.android.plugin")
                apply("org.jetbrains.kotlin.android")
                apply("org.jetbrains.kotlin.plugin.parcelize")
                apply("com.google.devtools.ksp")
                apply("com.google.gms.google-services")
                apply("com.google.firebase.crashlytics")
                apply("com.google.firebase.firebase-perf")
            }

            extensions.configure<BaseAppModuleExtension> {
                configureAndroid(commonExtension = this)
                configureAndroidApp(commonExtension = this)
                configureFirebasePerf(commonExtension = this)
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                "implementation"(libs.findLibrary("hilt.android").get())
                "ksp"(libs.findLibrary("hilt.compiler").get())
                "androidTestImplementation"(libs.findLibrary("hilt.android.testing").get())
                "kspAndroidTest"(libs.findLibrary("hilt.android.compiler").get())
                "implementation"(platform(libs.findLibrary("firebase.bom").get()))
                "implementation"(libs.findLibrary("firebase.crashlytics").get())
                "implementation"(libs.findLibrary("firebase.analytics").get())
                "implementation"(libs.findLibrary("firebase.perf").get())
            }

            extensions.configure<BaseAppModuleExtension> {
                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                }
            }
//            val kaptExtension = extensions.getByType<KaptExtension>()
//            kaptExtension.apply {
//                correctErrorTypes = true
//            }

//            packaging {
//                resources {
//                    excludes += "/META-INF/{AL2.0,LGPL2.1}"
//                }
//            }
        }
    }
}

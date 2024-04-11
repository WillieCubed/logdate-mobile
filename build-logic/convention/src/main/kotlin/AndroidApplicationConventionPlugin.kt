import app.logdate.buildlogic.configureAndroid
import app.logdate.buildlogic.configureAndroidApp
import app.logdate.buildlogic.configureFirebaseDeps
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
                configureFirebaseDeps(commonExtension = this)
                configureFirebasePerf(commonExtension = this)
            }

            with(extensions.getByType<VersionCatalogsExtension>().named("libs")) {
                dependencies {
                    "implementation"(findLibrary("hilt.android").get())
                    "ksp"(findLibrary("hilt.compiler").get())
                    "androidTestImplementation"(findLibrary("hilt.android.testing").get())
                    "kspAndroidTest"(findLibrary("hilt.android.compiler").get())
                }
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
        }
    }
}

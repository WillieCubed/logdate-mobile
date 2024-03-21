
import app.logdate.buildlogic.configureAndroid
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
            }

            extensions.configure<BaseAppModuleExtension> {
                configureAndroid(commonExtension = this)
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                "implementation"(libs.findLibrary("hilt.android").get())
                "ksp"(libs.findLibrary("hilt.compiler").get())
                "androidTestImplementation"(libs.findLibrary("hilt.android.testing").get())
                "kspAndroidTest"(libs.findLibrary("hilt.android.compiler").get())
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

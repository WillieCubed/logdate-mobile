
import app.logdate.buildlogic.configureAndroid
import app.logdate.buildlogic.configureBuildConfig
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("dagger.hilt.android.plugin")
                apply("org.jetbrains.kotlin.android")
                apply("org.jetbrains.kotlin.plugin.parcelize")
                apply("com.google.devtools.ksp")
            }

            extensions.configure<LibraryExtension> {
                configureAndroid(this)
                configureBuildConfig(this)
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                "implementation"(libs.findLibrary("hilt.android").get())
                "ksp"(libs.findLibrary("hilt.compiler").get())
                "androidTestImplementation"(libs.findLibrary("hilt.android.testing").get())
                "kspAndroidTest"(libs.findLibrary("hilt.android.compiler").get())
            }
        }
    }
}

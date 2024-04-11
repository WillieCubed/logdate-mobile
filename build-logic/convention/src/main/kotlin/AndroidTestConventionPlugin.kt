
import app.logdate.buildlogic.configureAndroid
import app.logdate.buildlogic.configureBuildConfig
import com.android.build.gradle.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.test")
                apply("org.jetbrains.kotlin.android")
                apply("com.google.devtools.ksp")
            }

            extensions.configure<TestExtension> {
                configureAndroid(this)
                configureBuildConfig(this)
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                "implementation"(libs.findLibrary("androidx.test.core.ktx").get())
                "implementation"(libs.findLibrary("androidx.compose.ui.test.junit4").get())
                "implementation"(libs.findLibrary("hilt.android").get())
                "implementation"(libs.findLibrary("hilt.android.testing").get())
                "ksp"(libs.findLibrary("hilt.compiler").get())
            }
        }
    }
}

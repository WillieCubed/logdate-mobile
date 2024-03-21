import com.android.build.gradle.TestExtension
import app.logdate.buildlogic.configureAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.plugin.KaptExtension

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
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                "implementation"(libs.findLibrary("androidx.test.core").get())
                "implementation"(libs.findLibrary("androidx.compose.ui.test.junit4").get())
                "implementation"(libs.findLibrary("hilt.android").get())
                "implementation"(libs.findLibrary("hilt.android.testing").get())
                "ksp"(libs.findLibrary("hilt.compiler").get())
            }

//            val kaptExtension = extensions.getByType<KaptExtension>()
//            kaptExtension.apply {
//                correctErrorTypes = true
//            }
        }
    }
}

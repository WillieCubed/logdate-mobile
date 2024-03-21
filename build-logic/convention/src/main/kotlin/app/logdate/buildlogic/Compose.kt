package app.logdate.buildlogic

import com.android.build.gradle.BaseExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.project

@Suppress("UnstableApiUsage")
internal fun Project.configureCompose(commonExtension: BaseExtension) {
    commonExtension.apply {
        buildFeatures.apply {
            compose = true
        }

        val libs: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

        composeOptions {
            kotlinCompilerExtensionVersion =
                libs.findVersion("androidxComposeCompiler").get().toString()
        }

        dependencies {
            "implementation"(platform(libs.findLibrary("androidx.compose.bom").get()))
            "implementation"(libs.findLibrary("androidx.compose.ui").get())
            "implementation"(libs.findLibrary("androidx.compose.material3").get())
            "implementation"(libs.findLibrary("androidx.ui.tooling.preview").get())
            // TODO: probably reconsider bundling icons in all Compose-enabled features
            "implementation"(libs.findLibrary("androidx.compose.icons.extended").get())
            "debugImplementation"(libs.findLibrary("androidx.ui.tooling").get())
            "androidTestImplementation"(libs.findLibrary("androidx.compose.ui.test.junit4").get())
            "androidTestImplementation"(project(":core:testing"))
        }
    }
}

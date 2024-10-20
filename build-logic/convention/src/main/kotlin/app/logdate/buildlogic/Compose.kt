package app.logdate.buildlogic

import com.android.build.gradle.BaseExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.project

internal fun Project.configureCompose(commonExtension: BaseExtension) {
    commonExtension.apply {
        buildFeatures.apply {
            compose = true
        }

        val libs: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

        with(libs) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            dependencies {
                "implementation"(platform(findLibrary("androidx.compose.bom").get()))
                "implementation"(findLibrary("androidx.compose.ui").get())
                "implementation"(findLibrary("androidx.compose.material3").get())
                "implementation"(findLibrary("androidx.ui.tooling.preview").get())
                // TODO: probably reconsider bundling icons in all Compose-enabled features
                "implementation"(findLibrary("androidx.compose.material.icons.extended").get())
                "debugImplementation"(findLibrary("androidx.ui.tooling").get())
                "androidTestImplementation"(findLibrary("androidx.compose.ui.test.junit4").get())
                "androidTestImplementation"(project(":core:testing"))
            }
        }
    }
}

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.dokka.gradle.DokkaTask

/**
 * A Gradle plugin that configures the project to use the Dokka documentation plugin.
 *
 * This plugin must be applied after the Android application (`com.android.application`) and
 * Kotlin (`org.jetbrains.kotlin.android`) plugins.
 */
class DokkaConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.dokka")
            }
//            allprojects {
//                apply(plugin = "org.jetbrains.dokka")
//            }
            tasks.withType<DokkaTask>().configureEach {
                dokkaSourceSets.configureEach {
//                    documentedVisibilities.set(
//                        setOf(
//                            Visibility.PUBLIC,
//                            Visibility.PROTECTED,
//                        )
//                    )

                    perPackageOption {
                        matchingRegex.set(".*internal.*")
                        suppress.set(true)
                    }
                }
            }
        }
    }
}

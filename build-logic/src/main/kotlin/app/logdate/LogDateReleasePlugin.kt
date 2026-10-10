package app.logdate

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

/**
 * Release identity, versioning and signing shared by LogDate's Android applications.
 *
 * The phone and the watch ship on one Play listing, and the Wear OS Data Layer only carries data
 * between apps with the same package name and signing key, so the values that decide those live
 * here rather than being written out in each app's build script.
 *
 * Applies to an Android application project as `id("app.logdate.android-release")`; read the
 * resolved values through the `logdateRelease` extension.
 */
class LogDateReleasePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.create("logdateRelease", LogDateReleaseExtension::class.java, target)
    }
}

open class LogDateReleaseExtension(
    private val project: Project,
) {
    /**
     * `studio.hypertext.logdate`, or the `logdate.applicationId` property when a rebuild has to run
     * under the retired `co.reasonabletech.logdate` identity that developer devices may still hold.
     */
    val applicationId: String =
        project.providers
            .gradleProperty("logdate.applicationId")
            .orElse("studio.hypertext.logdate")
            .get()

    /** Debug builds install beside a release build instead of replacing it. */
    val debugApplicationIdSuffix: String = ".debug"

    /**
     * From `LOGDATE_VERSION_CODE`, then the `logdate.versionCode` property, then 1 so a plain debug
     * build needs no setup. Publishing lets Play assign the real code.
     */
    val versionCode: Int =
        (System.getenv("LOGDATE_VERSION_CODE") ?: project.providers.gradleProperty("logdate.versionCode").orNull)
            ?.toIntOrNull() ?: 1

    val versionName: String =
        System.getenv("LOGDATE_VERSION_NAME")
            ?: project.providers.gradleProperty("logdate.versionName").orNull
            ?: "0.1.0"

    /** The Play track to publish to: `internal` unless `LOGDATE_PLAY_TRACK` or `logdate.play.track` says otherwise. */
    val playTrack: String =
        System.getenv("LOGDATE_PLAY_TRACK")
            ?: project.providers.gradleProperty("logdate.play.track").orNull
            ?: "internal"

    /** The same track on the Wear OS form factor, which Play names differently for Internal testing. */
    val wearPlayTrack: String get() = WearPlayTrack.forTrack(playTrack)

    val signing: ReleaseSigning? by lazy {
        ReleaseSigningResolver.resolve(
            environment = System.getenv(),
            gradleProperty = { name -> project.providers.gradleProperty(name).orNull },
            envFile = ReleaseSigningResolver.parseEnvFile(signingEnvFileLines()),
        )
    }

    /** Local benchmark and baseline-profile work may sign a release build with the debug key. Never for publishing. */
    val allowDebugReleaseSigning: Boolean =
        project.providers.gradleProperty("logdate.allowDebugReleaseSigning").orNull?.toBoolean() == true

    private val taskNames = project.gradle.startParameter.taskNames

    val playPublishRequested: Boolean =
        taskNames.any { it.contains("publish", ignoreCase = true) || it.contains("promote", ignoreCase = true) }

    val releaseTaskRequested: Boolean =
        taskNames.any { it.contains("Release", ignoreCase = true) || it.contains("Play", ignoreCase = true) }

    val baselineProfileRequested: Boolean = taskNames.any { it.contains("BaselineProfile", ignoreCase = true) }

    /** Whether release tasks sign with the debug key: only when nothing is being released or the override is on. */
    val signsWithDebugKey: Boolean
        get() = !releaseTaskRequested || baselineProfileRequested || (allowDebugReleaseSigning && signing == null)

    /**
     * Stops a release build that has no signing material, so a release is never quietly signed with
     * the debug key. Benchmarks and an explicit `-Plogdate.allowDebugReleaseSigning=true` are exempt.
     */
    fun requireSigningForReleaseTasks() {
        if (releaseTaskRequested && signing == null && !baselineProfileRequested && !allowDebugReleaseSigning) {
            error(
                "Release signing is not configured. Set LOGDATE_RELEASE_STORE_FILE, " +
                    "LOGDATE_RELEASE_STORE_PASSWORD, LOGDATE_RELEASE_KEY_ALIAS, and " +
                    "LOGDATE_RELEASE_KEY_PASSWORD, or explicitly pass " +
                    "-Plogdate.allowDebugReleaseSigning=true for a local non-publishing build.",
            )
        }
    }

    private fun signingEnvFileLines(): List<String> {
        val directory = System.getenv("LOGDATE_SIGNING_DIR") ?: "${System.getProperty("user.home")}/.logdate-signing"
        val file = File(directory, "production-upload.env")
        return if (file.isFile) file.readLines() else emptyList()
    }
}

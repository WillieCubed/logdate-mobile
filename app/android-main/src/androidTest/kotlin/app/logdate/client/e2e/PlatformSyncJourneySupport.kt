package app.logdate.client.e2e

import android.app.Instrumentation
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.client.testing.launch.ActivityLaunchTestOverrides
import app.logdate.client.testing.onboarding.OnboardingTestFixture
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Seeds the onboarding state the activity should launch into, then clears it again so the override
 * cannot leak into whatever test runs next — the field is process-global.
 */
internal class PlatformSyncLaunchRule(
    private val fixture: OnboardingTestFixture,
) : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                ActivityLaunchTestOverrides.onboardingFixture = fixture
                try {
                    base.evaluate()
                } finally {
                    ActivityLaunchTestOverrides.clear()
                }
            }
        }
}

/**
 * Skips the test unless a LogDate server is answering on the host.
 *
 * A skip rather than a failure: this journey needs infrastructure the standing instrumented suite
 * does not provide, and a red test for a missing server trains people to ignore it.
 */
internal class RequireHostServerRule(
    private val healthUrl: String = "${PlatformSyncJourneyE2ETest.HOST_SERVER}/health",
) : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                assumeTrue(
                    "No LogDate server answering at $healthUrl — start one with " +
                        "./tests/e2e/test-platform-sync.sh",
                    serverIsUp(),
                )
                base.evaluate()
            }
        }

    private fun serverIsUp(): Boolean =
        runCatching {
            val connection =
                (URL(healthUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3_000
                    readTimeout = 3_000
                }
            try {
                connection.responseCode == 200
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
}

/**
 * Points Credential Manager at the emulator-only test authenticator for the duration of the test,
 * and puts the previous value back afterwards.
 *
 * Google Password Manager refuses to mint a passkey without a signed-in Google account, so on a
 * bare managed device this is the only way to exercise the real ceremony rather than a fake.
 */
internal class CredentialProviderRule(
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
) : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val previous = shell("settings get secure credential_service").trim()
                shell("settings put secure credential_service ${PlatformSyncJourneyE2ETest.PROVIDER_SERVICE}")
                try {
                    base.evaluate()
                } finally {
                    if (previous.isBlank() || previous == "null") {
                        shell("settings delete secure credential_service")
                    } else {
                        shell("settings put secure credential_service $previous")
                    }
                }
            }
        }

    private fun shell(command: String): String =
        FileInputStream(
            instrumentation.uiAutomation.executeShellCommand(command).fileDescriptor,
        ).bufferedReader().use { it.readText() }
}

/**
 * The passkey ceremony leaves the app's window, so Compose can no longer see anything. This reads
 * whatever the system is showing through uiautomator, which is the only view available once
 * Credential Manager is on top.
 */
internal fun describeSystemUi(): String {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val xml =
        java.io.ByteArrayOutputStream()
            .also { runCatching { device.dumpWindowHierarchy(it) } }
            .toString(Charsets.UTF_8.name())
    val interesting =
        Regex("""(text|content-desc|resource-id|package)="([^"]+)"""")
            .findAll(xml)
            .map { "${it.groupValues[1]}=${it.groupValues[2]}" }
            .filter { it.substringAfter("=").isNotBlank() }
            .distinct()
            .take(60)
            .joinToString("\n  ")
    return "currentPackage=${device.currentPackageName}\n  $interesting"
}

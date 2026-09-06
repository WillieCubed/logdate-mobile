package app.logdate.client.e2e

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Preflight for the platform sync journey: the three things that have to be true on the device
 * before a real sign-in can be driven against a server running on the developer's machine.
 *
 * Each of these was an open question rather than an assumption, so they are asserted separately —
 * a failure here says which leg is missing instead of surfacing later as an opaque sign-in
 * timeout.
 */
@RunWith(AndroidJUnit4::class)
class PlatformSyncPreflightTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun `the test credential provider is installed alongside the app`() {
        val installed =
            runCatching {
                instrumentation.targetContext.packageManager
                    .getPackageInfo(PROVIDER_PACKAGE, 0)
            }.isSuccess

        assertTrue(
            installed,
            "$PROVIDER_PACKAGE is not on the device. Gradle Managed Devices install only the app " +
                "and test APKs, so the provider has to arrive via androidTestUtil.",
        )
    }

    @Test
    fun `the provider can be made the system credential service`() {
        shell("settings put secure credential_service $PROVIDER_SERVICE")

        assertEquals(
            PROVIDER_SERVICE,
            shell("settings get secure credential_service").trim(),
            "Credential Manager will not route the ceremony to the test provider.",
        )
    }

    /**
     * Skipped rather than failed when no server is listening, so the standing instrumented suite
     * does not depend on one. A cleartext denial arrives as an exception during connect and is
     * indistinguishable here from "nothing is running" — hence the explicit hint.
     */
    @Test
    fun `the host server is reachable over cleartext from the emulator`() {
        val url = URL("$HOST_SERVER/health")
        val connection = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 5_000 }
        val body =
            try {
                connection.responseCode.let { code ->
                    assertEquals(200, code, "Unexpected status from $url")
                }
                connection.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                assumeTrue(
                    "No LogDate server answering at $HOST_SERVER, so cleartext reachability was " +
                        "not exercised. If one IS running, this is the debug network security " +
                        "config failing to apply. Cause: $e",
                    false,
                )
                error("unreachable")
            } finally {
                connection.disconnect()
            }

        assertTrue(body.contains("\"status\""), "Unexpected /health payload: $body")
    }

    private fun shell(command: String): String =
        FileInputStream(
            instrumentation.uiAutomation.executeShellCommand(command).fileDescriptor,
        ).bufferedReader().use { it.readText() }

    private companion object {
        const val PROVIDER_PACKAGE = "app.logdate.tools.passkeyprovider"
        const val PROVIDER_SERVICE = "$PROVIDER_PACKAGE/$PROVIDER_PACKAGE.TestPasskeyProviderService"

        /** 10.0.2.2 is the developer's machine as seen from inside a standard AVD. */
        const val HOST_SERVER = "http://10.0.2.2:8765"
    }
}

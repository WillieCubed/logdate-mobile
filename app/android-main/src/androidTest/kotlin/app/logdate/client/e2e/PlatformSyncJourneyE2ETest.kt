package app.logdate.client.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.logdate.client.MainActivity
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.testing.launch.ActivityLaunchTestOverrides
import app.logdate.client.testing.onboarding.OnboardingTestFixture
import app.logdate.feature.core.account.CLOUD_ACCOUNT_DISPLAY_NAME_CONTINUE_TAG
import app.logdate.feature.core.account.CLOUD_ACCOUNT_DISPLAY_NAME_FIELD_TAG
import app.logdate.feature.core.account.CLOUD_ACCOUNT_DISPLAY_NAME_ROOT_TAG
import app.logdate.feature.core.account.CLOUD_ACCOUNT_PASSKEY_CREATE_TAG
import app.logdate.feature.core.account.CLOUD_ACCOUNT_PASSKEY_ROOT_TAG
import app.logdate.feature.core.account.CLOUD_ACCOUNT_USERNAME_CONTINUE_TAG
import app.logdate.feature.core.account.CLOUD_ACCOUNT_USERNAME_FIELD_TAG
import app.logdate.feature.core.account.CLOUD_ACCOUNT_USERNAME_ROOT_TAG
import app.logdate.feature.onboarding.flow.OnboardingEntryMode
import app.logdate.feature.onboarding.ui.CLOUD_ACCOUNT_SETUP_PRIMARY_ACTION_TAG
import app.logdate.feature.onboarding.ui.MEMORIES_IMPORT_INFO_CONTINUE_TAG
import app.logdate.feature.onboarding.ui.MEMORY_SELECTION_CONTINUE_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_OVERVIEW_CONTINUE_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_START_GET_STARTED_TAG
import app.logdate.feature.onboarding.ui.ONBOARDING_START_ROOT_TAG
import app.logdate.feature.onboarding.ui.CLOUD_ACCOUNT_SETUP_ROOT_TAG
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.config.LogDateConfigRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The journey nothing else covers: a real device creating a real account against a real server,
 * through the onboarding UI, with a WebAuthn ceremony the relying party actually verifies.
 *
 * The JVM suite in `:integration:server-client-e2e` proves the protocol from a Kotlin client, and
 * `OnboardingJourneyE2ETest` walks the UI but skips cloud account setup entirely. Between them sits
 * the seam this covers: the app's own Credential Manager call, its own HTTP stack, and its own
 * session storage, against a server that verifies the signature.
 *
 * Requires a server on the host — see `tests/e2e/test-platform-sync.sh`. The test skips rather than
 * fails when there is none, so it can sit in the standing suite.
 */
@RunWith(AndroidJUnit4::class)
class PlatformSyncJourneyE2ETest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Unique per run: the server rejects a username that already exists. */
    private val username = "e2e_${Random.nextInt(100_000, 999_999)}"
    private val displayName = "Platform E2E"

    private val koinRule =
        OnboardingKoinModuleOverrideRule(
            module {
                // Only this one binding is replaced. Sync, session storage and the repositories
                // stay real, because they are the thing under test.
                single<LogDateConfigRepository> {
                    DefaultLogDateConfigRepository(initialBackendUrl = HOST_SERVER)
                }
            },
        )

    private val launchRule =
        PlatformSyncLaunchRule(
            // Land on the account step inside the real onboarding flow: CONTINUE_SETUP drops the
            // app-overview and memory-import steps, and a completed personal intro drops the first.
            OnboardingTestFixture(
                entryMode = OnboardingEntryMode.CONTINUE_SETUP,
                hasPersonalIntro = true,
                hasCloudAccount = false,
            ),
        )

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain =
        RuleChain
            .outerRule(RequireHostServerRule())
            .around(CredentialProviderRule(instrumentation))
            .around(koinRule)
            .around(launchRule)
            .around(composeRule)

    @Test
    fun `creating an account during onboarding registers it on the server`() {
        advanceToCloudAccountStep()
        composeRule.onNodeWithTag(CLOUD_ACCOUNT_SETUP_PRIMARY_ACTION_TAG).performClick()

        // The display-name step is dropped when onboarding already captured a name, so it is
        // present or absent depending on the seeded fixture rather than always shown.
        if (waitForEitherTag(CLOUD_ACCOUNT_DISPLAY_NAME_ROOT_TAG, CLOUD_ACCOUNT_USERNAME_ROOT_TAG)
            == CLOUD_ACCOUNT_DISPLAY_NAME_ROOT_TAG
        ) {
            composeRule.onNodeWithTag(CLOUD_ACCOUNT_DISPLAY_NAME_FIELD_TAG).performTextInput(displayName)
            composeRule.onNodeWithTag(CLOUD_ACCOUNT_DISPLAY_NAME_CONTINUE_TAG).performClick()
        }

        awaitTag(CLOUD_ACCOUNT_USERNAME_ROOT_TAG, timeoutMillis = 15_000)
        composeRule.onNodeWithTag(CLOUD_ACCOUNT_USERNAME_FIELD_TAG).performTextInput(username)
        // Continue stays disabled until the server has answered the availability check.
        composeRule.waitUntil(timeoutMillis = 20_000) {
            runCatching {
                composeRule.onNodeWithTag(CLOUD_ACCOUNT_USERNAME_CONTINUE_TAG).assertIsEnabled()
            }.isSuccess
        }
        composeRule.onNodeWithTag(CLOUD_ACCOUNT_USERNAME_CONTINUE_TAG).performClick()

        awaitTag(CLOUD_ACCOUNT_PASSKEY_ROOT_TAG, timeoutMillis = 15_000)
        composeRule.onNodeWithTag(CLOUD_ACCOUNT_PASSKEY_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(CLOUD_ACCOUNT_PASSKEY_CREATE_TAG).performClick()

        // The ceremony leaves the app: Credential Manager takes over the screen and asks the user
        // to confirm before the provider is invoked at all. Nothing in Compose can see or drive
        // that, so it goes through uiautomator.
        confirmCredentialManagerSheet()

        val session =
            waitForSession(timeoutMillis = 60_000)
                ?: error(
                    "No session after the passkey ceremony. System UI at that point:\n" +
                        describeSystemUi(),
                )

        assertTrue(session.accessToken.isNotBlank(), "Access token is blank")

        // The real proof: ask the server, not the app.
        val me = getJson("$HOST_SERVER/api/v1/auth/me", session.accessToken)
        assertTrue(
            me.contains(username),
            "Server does not know the account the app just created. /auth/me said: $me",
        )
    }

    /**
     * Onboarding always opens on the start screen, whatever progress the fixture seeds, and the
     * steps between it and the account step vary with entry mode and device capability. Rather
     * than encode one exact path, this clicks whichever known continue action is on screen until
     * the account step appears — so an added or reordered step does not break the journey.
     */
    private fun advanceToCloudAccountStep() {
        awaitTag(ONBOARDING_START_ROOT_TAG, timeoutMillis = 30_000)
        // The start screen holds a ~3s intro animation before its actions are hittable.
        awaitTag(ONBOARDING_START_GET_STARTED_TAG, timeoutMillis = 20_000)
        composeRule.onNodeWithTag(ONBOARDING_START_GET_STARTED_TAG).performClick()

        val passThrough =
            listOf(
                ONBOARDING_OVERVIEW_CONTINUE_TAG,
                MEMORIES_IMPORT_INFO_CONTINUE_TAG,
                MEMORY_SELECTION_CONTINUE_TAG,
            )
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            if (tagExists(CLOUD_ACCOUNT_SETUP_ROOT_TAG)) return
            val next = passThrough.firstOrNull { tagExists(it) }
            if (next != null) {
                runCatching { composeRule.onNodeWithTag(next).performClick() }
            }
            composeRule.waitForIdle()
            Thread.sleep(250)
        }
        error(
            "Never reached the cloud account step. On screen instead:\n" +
                runCatching { composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 100) }
                    .getOrNull(),
        )
    }

    /**
     * Confirms the system passkey sheet.
     *
     * On this emulator it reads "Create passkey to sign in to LogDate?" over
     * "LogDate Test Authenticator", with a Continue button. The loop tolerates the flow taking
     * more than one tap, and stops as soon as a session exists — the sheet is a means, not the
     * assertion.
     */
    private fun confirmCredentialManagerSheet(timeoutMillis: Long = 45_000) {
        val device = UiDevice.getInstance(instrumentation)
        device.wait(Until.hasObject(By.pkg(CREDENTIAL_MANAGER_PACKAGE)), 20_000)

        val button =
            CONSENT_LABELS.firstNotNullOfOrNull { label ->
                device.wait(Until.findObject(By.text(label).pkg(CREDENTIAL_MANAGER_PACKAGE)), 5_000)
            } ?: error("No confirm action on the passkey sheet. System UI:\n${describeSystemUi()}")

        // Exactly once. Every tap starts a whole new ceremony, and begin+complete both draw on the
        // same 5-per-hour signup budget, so a retry loop here exhausts the rate limit rather than
        // recovering from anything.
        button.click()
        device.wait(Until.gone(By.pkg(CREDENTIAL_MANAGER_PACKAGE)), timeoutMillis)
    }

    /** Returns whichever of the two tags appears first, so an optional step can be detected. */
    private fun waitForEitherTag(
        first: String,
        second: String,
        timeoutMillis: Long = 15_000,
    ): String {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (tagExists(first)) return first
            if (tagExists(second)) return second
            composeRule.waitForIdle()
            Thread.sleep(250)
        }
        error("Neither '$first' nor '$second' appeared within ${timeoutMillis}ms")
    }

    /**
     * A bare `waitUntil` failure says only that a tag never appeared, which is useless when the
     * question is *which* screen the app is actually on. This prints the semantics tree into the
     * failure so the report answers that.
     */
    private fun awaitTag(
        tag: String,
        timeoutMillis: Long,
    ) {
        try {
            composeRule.waitUntil(timeoutMillis = timeoutMillis) { tagExists(tag) }
        } catch (e: Throwable) {
            val tree = runCatching { composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 100) }
            error("Never saw '$tag' within ${timeoutMillis}ms. On screen instead:\n${tree.getOrNull()}")
        }
    }

    private fun tagExists(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() ||
            composeRule
                .onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()

    private fun waitForSession(timeoutMillis: Long): app.logdate.client.datastore.UserSession? {
        val storage = GlobalContext.get().get<SessionStorage>()
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val session = runBlocking { storage.getSession() }
            if (session != null) return session
            // Real wall-clock waiting: the ceremony happens in another process, so there is no
            // composition to advance and the default auto-advancing clock is already running.
            Thread.sleep(500)
        }
        return null
    }

    private fun getJson(url: String, token: String): String {
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
        return try {
            assertEquals(200, connection.responseCode, "Unexpected status from $url")
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    internal companion object {
        const val HOST_SERVER = "http://10.0.2.2:8765"
        const val PROVIDER_PACKAGE = "app.logdate.tools.passkeyprovider"
        const val PROVIDER_SERVICE = "$PROVIDER_PACKAGE/$PROVIDER_PACKAGE.TestPasskeyProviderService"
        const val CREDENTIAL_MANAGER_PACKAGE = "com.android.credentialmanager"

        /** Confirm affordances the sheet has used, most specific first. */
        val CONSENT_LABELS = listOf("Continue", "Create passkey", "Use passkey", "OK")
    }
}

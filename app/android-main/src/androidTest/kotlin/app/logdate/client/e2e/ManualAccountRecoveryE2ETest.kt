package app.logdate.client.e2e

import android.app.Application
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import app.logdate.client.MainActivity
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.crypto.Bip39
import app.logdate.client.device.crypto.CryptoManager
import app.logdate.client.device.crypto.IdentityKeyFingerprint
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.device.storage.SecureStorage
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.media.MediaManager
import app.logdate.client.networking.AddPasskeyRequest
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import app.logdate.client.networking.PasskeyApiClientContract
import app.logdate.client.networking.ServerDiscoveryClient
import app.logdate.client.permissions.PasskeyManager
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.feature.onboarding.ui.ONBOARDING_START_FROM_BACKUP_TAG
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.PasskeyCredentialResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_recovery_phrase_title
import logdate.client.feature.core.generated.resources.account_title
import logdate.client.feature.core.generated.resources.recovery_phrase_enter
import logdate.client.feature.core.generated.resources.recovery_phrase_entry_settings_mismatch_error
import logdate.client.feature.core.generated.resources.recovery_phrase_entry_settings_submit
import logdate.client.feature.core.generated.resources.recovery_phrase_title
import logdate.client.feature.core.generated.resources.settings
import logdate.client.feature.core.generated.resources.sign_in_with_passkey
import org.jetbrains.compose.resources.getString
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.koin.core.context.GlobalContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Explicit two-phase managed-device journey; the host retains the same installation between phases. */
class ManualAccountRecoveryE2ETest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val koin get() = GlobalContext.get()
    private val compose = createEmptyComposeRule()
    private val checkpoint get() = File(context.noBackupFilesDir, "manual-account-recovery-checkpoint.json")

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(CredentialProviderRule(instrumentation)).around(compose)

    @Test
    fun ordinarySignInThenSettingsPhraseRecoversOriginalContent() =
        runBlocking {
            val phase = InstrumentationRegistry.getArguments().getString("logdate.recoveryAcceptanceMode")
            assumeTrue(phase in setOf("manual-account-source-proof", "manual-account-recover", "manual-account-offline"))
            require(Build.HARDWARE in setOf("ranchu", "goldfish") && context.packageName.endsWith(".debug"))
            ensureLogDateApplicationStarted(context.applicationContext as Application)
            koin.get<LogDateConfigDataSource>().awaitConfigurationLoaded()
            if (phase == "manual-account-source-proof") {
                publishSourceProof()
                return@runBlocking
            }
            val fixture = readFixture()
            val config = koin.get<LogDateConfigRepository>()
            if (phase == "manual-account-recover") {
                // The fixture selects a validated loopback server before MainActivity or account recovery opens.
                assertNull(assertNotNull(koin.get<SessionStorage>().captureAuthenticationSnapshot()).session)
                config.updateBackendUrl(fixture.origin)
            }
            assertEquals(fixture.origin, config.getCurrentBackendUrl())
            ActivityScenario.launch(MainActivity::class.java).use {
                if (phase == "manual-account-offline") {
                    assertColdOfflineRecovery(fixture)
                } else {
                    recover(fixture)
                }
            }
        }

    private suspend fun publishSourceProof() {
        val seed =
            instrumentation.context.assets
                .open("recovery-acceptance.json")
                .bufferedReader()
                .use { JSONObject(it.readText()) }
        val owner = seed.getString("accountId")
        val origin = seed.getString("serverOrigin")
        val session = assertNotNull(assertNotNull(koin.get<SessionStorage>().captureAuthenticationSnapshot()).session)
        assertEquals(origin, session.origin)
        assertEquals(owner, session.session.accountId)
        assertEquals(owner, koin.get<CanonicalOwnerProvider>().getCanonicalOwnerId())
        val ids = seed.getJSONObject("ids")
        val notes = koin.get<JournalNotesRepository>()
        val text = assertNotNull(notes.getNoteById(Uuid.parse(ids.getString("textNote"))) as? JournalNote.Text)
        val image = assertNotNull(notes.getNoteById(Uuid.parse(ids.getString("imageNote"))) as? JournalNote.Image)
        assertEquals("Encrypted text ${seed.getString("marker")}", text.content)
        val bytes = koin.get<MediaManager>().readMedia(image.mediaRef).data
        assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val proof =
            JSONObject()
                .put("synthetic", true)
                .put("origin", origin)
                .put("owner", owner)
                .put("bootstrapAccessToken", session.session.accessToken)
                .put("recoveryWords", seed.getJSONArray("recoveryWords"))
                .put("sourceFingerprint", fingerprint())
                .put(
                    "sourceDevice",
                    koin
                        .get<DeviceIdProvider>()
                        .getDeviceId()
                        .value
                        .toString(),
                ).put("textNoteId", text.uid.toString())
                .put("text", text.content)
                .put("imageNoteId", image.uid.toString())
                .put("imageSha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val destination = File(context.noBackupFilesDir, "manual-account-recovery-source-proof.json")
        require(!destination.exists()) { "Source proof requires a fresh task-owned source installation" }
        destination.writeText(proof.toString())
    }

    private suspend fun recover(fixture: Fixture) {
        require(!checkpoint.exists()) { "Manual recovery requires a fresh task-owned installation" }
        val sessions = koin.get<SessionStorage>()
        val identity = koin.get<IdentityKeyManager>()
        val notes = koin.get<JournalNotesRepository>()
        assertNull(assertNotNull(sessions.captureAuthenticationSnapshot()).session)
        assertKeysAbsent()
        assertTrue(notes.allNotesObserved.first().isEmpty())
        val descriptor = koin.get<ServerDiscoveryClient>().discoverServer(fixture.origin).getOrThrow()
        koin.get<LogDateConfigRepository>().updateServerDescriptor(descriptor)
        val owner = koin.get<CanonicalOwnerProvider>()
        assertFalse(owner.hasBoundOwner())
        val installation =
            koin
                .get<DeviceIdProvider>()
                .getDeviceId()
                .value
                .toString()

        assertNotEquals(fixture.sourceDevice, installation, "The receiving installation must be distinct from its source")

        // Register a credential for the EXISTING synthetic fixture account. Its bootstrap token is
        // never installed as the app session; only the subsequent real assertion may authenticate it.
        val api = koin.get<PasskeyApiClientContract>()
        assertEquals(
            fixture.owner,
            api
                .getAccountInfo(fixture.bootstrapToken)
                .getOrThrow()
                .id
                .toString(),
        )
        val options = api.beginAddPasskey(fixture.bootstrapToken).getOrThrow()
        val credential = ceremony { koin.get<PasskeyManager>().registerPasskey(options).getOrThrow() }
        api
            .completeAddPasskey(
                fixture.bootstrapToken,
                AddPasskeyRequest(
                    options.challenge,
                    Json { ignoreUnknownKeys = true }.decodeFromString<PasskeyCredentialResponse>(credential),
                    "Manual recovery acceptance",
                ),
            ).getOrThrow()
        assertNull(assertNotNull(sessions.captureAuthenticationSnapshot()).session)
        assertKeysAbsent()

        assertFalse(owner.hasBoundOwner(), "Only ordinary sign-in may adopt the replacement installation")
        // Follow the fresh-install action and normal sign-in route. No repository authentication
        // call, NavKey injection, onboarding override, or direct composable mounting may skip it.
        waitForTag(ONBOARDING_START_FROM_BACKUP_TAG)
        compose.onNodeWithTag(ONBOARDING_START_FROM_BACKUP_TAG).performClick()
        val signIn = getString(Res.string.sign_in_with_passkey)
        compose.waitUntil(30_000) { compose.onAllNodes(hasText(signIn)).fetchSemanticsNodes().size == 1 }
        ceremony {
            compose.onNodeWithText(signIn).performClick()
            withTimeout(90_000) {
                while (sessions
                        .captureAuthenticationSnapshot()
                        ?.session
                        ?.session
                        ?.accountId != fixture.owner
                ) {
                    delay(100)
                }
            }
        }
        assertTrue(owner.hasBoundOwner())
        assertEquals(fixture.owner, owner.getCanonicalOwnerId())
        assertEquals(fixture.owner, sessions.getOriginBoundSession()?.session?.accountId)
        assertEquals(fixture.origin, sessions.getOriginBoundSession()?.origin)
        assertKeysAbsent()
        assertNull(notes.getNoteById(fixture.textId), "Account access must not fabricate readable journal content")
        assertNull(notes.getNoteById(fixture.imageId), "Account access must not fabricate a media original")

        // WelcomeBack and first sync must finish through the real navigation host before Settings
        // becomes available. Both shipped Home presentations expose an existing settings action.
        compose.waitUntil(120_000) {
            compose.onAllNodesWithTag("logdate_home_settings").fetchSemanticsNodes().size == 1 ||
                compose.onAllNodesWithTag("workspace_account").fetchSemanticsNodes().size == 1
        }
        assertKeysAbsent()
        if (compose.onAllNodesWithTag("logdate_home_settings").fetchSemanticsNodes().size == 1) {
            compose.onNodeWithTag("logdate_home_settings").performClick()
        } else {
            compose.onNodeWithTag("workspace_account").performClick()
            compose.onNodeWithText(getString(Res.string.settings)).performClick()
        }
        clickSettingsText(getString(Res.string.account_title))
        clickSettingsText(getString(Res.string.account_recovery_phrase_title))
        clickSettingsText(getString(Res.string.recovery_phrase_enter))
        compose.waitUntil(15_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 12 }
        val wrongWords = Bip39.generateMnemonic(ByteArray(16) { 42.toByte() })
        assertNotEquals(fixture.words, wrongWords, "The rejection probe must use a different checksum-valid phrase")
        assertTrue(koin.get<CryptoManager>().validateRecoveryPhrase(wrongWords))
        wrongWords.forEachIndexed { index, word ->
            compose.onAllNodes(hasSetTextAction())[index].performScrollTo().performTextInput(word)
        }
        compose.onNodeWithText(getString(Res.string.recovery_phrase_entry_settings_submit)).performScrollTo().performClick()
        val mismatchCopy = getString(Res.string.recovery_phrase_entry_settings_mismatch_error)
        compose.waitUntil(30_000) { compose.onAllNodes(hasText(mismatchCopy)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText(mismatchCopy).assertIsDisplayed()
        assertEquals(12, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        assertKeysAbsent()
        assertNull(notes.getNoteById(fixture.textId), "A rejected phrase must not expose encrypted content")
        assertNull(notes.getNoteById(fixture.imageId), "A rejected phrase must not expose encrypted media")
        fixture.words.forEachIndexed {
            index,
            word,
            ->
            compose.onAllNodes(hasSetTextAction())[index].performScrollTo().performTextReplacement(word)
        }
        compose.onNodeWithText(getString(Res.string.recovery_phrase_entry_settings_submit)).performScrollTo().performClick()
        // SettingsEntries wires successful recovery back to RecoveryPhraseRoute. The entry form
        // disappearing proves its real navigation callback ran, instead of a test callback.
        compose.waitUntil(30_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
        val phraseTitle = getString(Res.string.recovery_phrase_title)
        compose.waitUntil(15_000) { compose.onAllNodes(hasText(phraseTitle)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText(phraseTitle).assertIsDisplayed()
        assertTrue(identity.hasIdentityKey())
        assertEquals(fixture.fingerprint, fingerprint())
        assertEquals(fixture.owner, owner.getCanonicalOwnerId())
        assertEquals(
            installation,
            koin
                .get<DeviceIdProvider>()
                .getDeviceId()
                .value
                .toString(),
        )
        withTimeout(90_000) {
            while (!contentReady(fixture)) {
                delay(500)
            }
        }
        assertContent(fixture)
        checkpoint.writeText(
            JSONObject()
                .put("owner", fixture.owner)
                .put("fingerprint", fingerprint())
                .put("device", installation)
                .put("process", Process.myPid())
                .put("processStart", Process.getStartUptimeMillis())
                .toString(),
        )
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1 }
    }

    private fun clickSettingsText(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().size == 1 }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performScrollTo().performClick()
    }

    private suspend fun assertColdOfflineRecovery(fixture: Fixture) {
        val accepted = JSONObject(checkpoint.readText())
        assertTrue(
            accepted.getInt("process") != Process.myPid() || accepted.getLong("processStart") != Process.getStartUptimeMillis(),
            "Offline acceptance requires a new app process after the recovery phase",
        )
        withTimeout(15_000) {
            koin.get<NetworkAvailabilityMonitor>().observeNetwork().first { it is NetworkState.NotConnected }
        }
        assertFalse(serverReachable(fixture.origin), "The cold-start proof requires actual loss of local server connectivity")
        val session = assertNotNull(assertNotNull(koin.get<SessionStorage>().captureAuthenticationSnapshot()).session)
        assertEquals(fixture.owner, accepted.getString("owner"))
        assertEquals(fixture.fingerprint, accepted.getString("fingerprint"))
        assertEquals(fixture.owner, koin.get<CanonicalOwnerProvider>().getCanonicalOwnerId())
        assertEquals(fixture.owner, session.session.accountId)
        assertEquals(
            accepted.getString("device"),
            koin
                .get<DeviceIdProvider>()
                .getDeviceId()
                .value
                .toString(),
        )
        assertEquals(fixture.origin, session.origin)
        assertEquals(accepted.getString("fingerprint"), fingerprint())
        assertContent(fixture)
    }

    private suspend fun assertKeysAbsent() {
        assertFalse(koin.get<IdentityKeyManager>().hasIdentityKey())
        assertNull(koin.get<SecureStorage>().getString("identity_key_v1"))
        assertNull(koin.get<SecureStorage>().getString("media_payload_key_v1"))
    }

    private suspend fun contentReady(fixture: Fixture): Boolean {
        val notes = koin.get<JournalNotesRepository>()
        val text = notes.getNoteById(fixture.textId) as? JournalNote.Text ?: return false
        val image = notes.getNoteById(fixture.imageId) as? JournalNote.Image ?: return false
        return text.content == fixture.text && koin.get<MediaManager>().exists(image.mediaRef)
    }

    private suspend fun assertContent(fixture: Fixture) {
        val notes = koin.get<JournalNotesRepository>()
        assertEquals(fixture.text, (notes.getNoteById(fixture.textId) as? JournalNote.Text)?.content)
        val image = assertNotNull(notes.getNoteById(fixture.imageId) as? JournalNote.Image)
        val bytes = koin.get<MediaManager>().readMedia(image.mediaRef).data
        assertEquals(fixture.imageSha256, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size), "Recovered original must decode offline")
    }

    private suspend fun fingerprint(): String =
        IdentityKeyFingerprint.of(koin.get<IdentityKeyManager>().getIdentityKey(), koin.get<CryptoManager>())

    private suspend fun <T> ceremony(action: suspend () -> T): T =
        coroutineScope {
            val pending = async(Dispatchers.Default) { action() }
            val device = UiDevice.getInstance(instrumentation)
            try {
                withTimeout(45_000) {
                    while (!pending.isCompleted) {
                        for (label in listOf("Continue", "Create passkey", "Use passkey", "OK")) {
                            device.findObject(By.text(label).pkg("com.google.android.gms"))?.click()
                            device.findObject(By.text(label).pkg("com.android.credentialmanager"))?.click()
                        }
                        delay(100)
                    }
                    pending.await()
                }
            } finally {
                pending.cancel()
            }
        }

    private fun readFixture(): Fixture {
        val file = File(context.noBackupFilesDir, "manual-account-recovery.json")
        require(file.isFile && file.length() in 1..32_768) { "Private source proof is missing or oversized" }
        val json = JSONObject(file.readText())
        require(json.getBoolean("synthetic")) { "A synthetic source proof is required" }
        val origin = json.getString("origin")
        val uri = URI(origin)
        require(uri.scheme == "http" && uri.host == "10.0.2.2" && uri.port in 1..65535)
        require(uri.userInfo == null && uri.rawPath.orEmpty().isEmpty() && uri.rawQuery == null && uri.rawFragment == null)
        val words = json.getJSONArray("recoveryWords").let { values -> (0 until values.length()).map(values::getString) }
        require(words.size == 12 && words.all { it.matches(Regex("[a-z]+")) })
        val imageSha = json.getString("imageSha256").also { require(it.matches(Regex("[0-9a-f]{64}"))) }
        return Fixture(
            origin,
            json.getString("owner").also { Uuid.parse(it) },
            json.getString("bootstrapAccessToken").also { require(it.isNotBlank()) },
            words,
            json.getString("sourceFingerprint").also { require(it.isNotBlank()) },
            json.getString("sourceDevice").also { Uuid.parse(it) },
            Uuid.parse(json.getString("textNoteId")),
            json.getString("text"),
            Uuid.parse(json.getString("imageNoteId")),
            imageSha,
        )
    }

    private fun serverReachable(origin: String): Boolean {
        val connection =
            (URL("$origin/health").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_000
                readTimeout = 2_000
            }
        return try {
            connection.responseCode in 100..599
        } catch (_: java.io.IOException) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private data class Fixture(
        val origin: String,
        val owner: String,
        val bootstrapToken: String,
        val words: List<String>,
        val fingerprint: String,
        val sourceDevice: String,
        val textId: Uuid,
        val text: String,
        val imageId: Uuid,
        val imageSha256: String,
    )
}

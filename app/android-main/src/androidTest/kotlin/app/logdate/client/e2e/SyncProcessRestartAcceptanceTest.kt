package app.logdate.client.e2e

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.networking.ServerDiscoveryClient
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.feature.core.sync.SyncIssueRetryFeedback
import app.logdate.feature.core.sync.SyncIssuesViewModel
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.Journal
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.koin.core.context.GlobalContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** The orchestrator restarts the app process between these ordered tests without clearing app data. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SyncProcessRestartAcceptanceTest {
    @Test
    fun a_queueOfflineEditsAndDeletion(): Unit = runBlocking {
        val fixture = fetchFixtureOrSkip() ?: return@runBlocking
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences(PREFERENCES, 0)
        assertFalse(preferences.getBoolean(PREPARED, false), "acceptance emulator must start with a clean profile")

        val koin = GlobalContext.get()
        val config = koin.get<LogDateConfigRepository>()
        koin.get<LogDateConfigDataSource>().awaitConfigurationLoaded()
        config.updateBackendUrl(fixture.origin)
        val descriptor = koin.get<ServerDiscoveryClient>().discoverServer(fixture.origin).getOrThrow()
        assertEquals(fixture.origin, descriptor.serverOrigin)
        config.updateServerDescriptor(descriptor)

        val owner = koin.get<CanonicalOwnerProvider>()
        assertTrue(owner.adoptRemoteOwnerIfUninitialized(fixture.accountId))
        val identity = koin.get<IdentityKeyManager>()
        identity.recoverIdentity(fixture.recoveryWords)
        val sessions = koin.get<SessionStorage>()
        sessions.saveSession(UserSession(fixture.accessToken, fixture.refreshToken, fixture.accountId))

        val journals = koin.get<JournalRepository>()
        val notes = koin.get<JournalNotesRepository>()
        val sync = koin.get<DefaultSyncManager>()
        val journalId = Uuid.random()
        val deletedNoteId = Uuid.random()
        val offlineNoteId = Uuid.random()
        val journal = Journal(id = journalId, title = "Process restart acceptance")
        val now = Clock.System.now()
        journals.create(journal)
        notes.create(
            JournalNote.Text(
                uid = deletedNoteId,
                creationTimestamp = now,
                lastUpdated = now,
                content = "Delete after going offline",
            ),
            journalId,
        )
        awaitSettled(sync)

        val device = UiDevice.getInstance(instrumentation)
        var leftOfflineForRestart = false
        try {
            setNetworkEnabled(device, false)
            assertTrue(awaitServerUnavailable(fixture.origin), "server remained reachable after airplane mode was enabled")

            notes.removeById(deletedNoteId)
            notes.create(
                JournalNote.Text(
                    uid = offlineNoteId,
                    creationTimestamp = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                    content = "Offline edit survives process restart",
                ),
                journalId,
            )
            sync.fullSync()

            val status = sync.getSyncStatus()
            assertTrue(status.pendingUploads >= 2, "offline create and deletion were not durably queued")
            assertNull(notes.getNoteById(deletedNoteId), "local deletion did not apply")
            assertEquals(
                "Offline edit survives process restart",
                (notes.getNoteById(offlineNoteId) as? JournalNote.Text)?.content,
            )

            preferences.edit()
                .putInt(PREVIOUS_PID, Process.myPid())
                .putString(ACCOUNT_ID, fixture.accountId)
                .putString(SERVER_ORIGIN, fixture.origin)
                .putString(JOURNAL_ID, journalId.toString())
                .putString(DELETED_NOTE_ID, deletedNoteId.toString())
                .putString(OFFLINE_NOTE_ID, offlineNoteId.toString())
                .putBoolean(PREPARED, true)
                .commit()
            leftOfflineForRestart = true
        } finally {
            if (!leftOfflineForRestart) setNetworkEnabled(device, true)
        }
    }

    @Test
    fun b_restartAndRetryRetainsOfflineEdits(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences(PREFERENCES, 0)
        assertTrue(preferences.getBoolean(PREPARED, false), "offline setup test did not complete")
        assertFalse(
            Process.myPid() == preferences.getInt(PREVIOUS_PID, -1),
            "Android Test Orchestrator did not restart the app process between phases",
        )

        val koin = GlobalContext.get()
        koin.get<LogDateConfigDataSource>().awaitConfigurationLoaded()
        val owner = koin.get<CanonicalOwnerProvider>()
        val identity = koin.get<IdentityKeyManager>()
        val sessions = koin.get<SessionStorage>()
        assertTrue(sessions.hasValidSession(), "persisted session was not loaded after process restart")
        val session = assertNotNull(sessions.getOriginBoundSession(), "signed-in session did not survive process restart")
        val fixture =
            Fixture(
                origin = requireNotNull(preferences.getString(SERVER_ORIGIN, null)),
                accountId = requireNotNull(preferences.getString(ACCOUNT_ID, null)),
                accessToken = session.session.accessToken,
                refreshToken = session.session.refreshToken,
                recoveryWords = emptyList(),
            )
        assertEquals(fixture.origin, session.origin)
        assertEquals(fixture.accountId, session.session.accountId)
        assertEquals(fixture.accountId, owner.getCanonicalOwnerId())
        assertTrue(identity.hasIdentityKey(), "recovery identity did not survive process restart")

        val journalId = Uuid.parse(requireNotNull(preferences.getString(JOURNAL_ID, null)))
        val deletedNoteId = Uuid.parse(requireNotNull(preferences.getString(DELETED_NOTE_ID, null)))
        val offlineNoteId = Uuid.parse(requireNotNull(preferences.getString(OFFLINE_NOTE_ID, null)))
        val notes = koin.get<JournalNotesRepository>()
        val sync = koin.get<DefaultSyncManager>()
        assertTrue(sync.getSyncStatus().pendingUploads >= 2, "pending edits disappeared after process restart")
        assertNull(notes.getNoteById(deletedNoteId), "pending local deletion was lost after process restart")
        assertEquals(
            "Offline edit survives process restart",
            (notes.getNoteById(offlineNoteId) as? JournalNote.Text)?.content,
        )
        assertNotNull(koin.get<JournalRepository>().getJournalById(journalId))

        val device = UiDevice.getInstance(instrumentation)
        setNetworkEnabled(device, true)
        assertTrue(awaitServer(fixture.origin), "persistent server did not become reachable after reconnect")

        val retryViewModel =
            SyncIssuesViewModel(
                syncManager = sync,
                journalRepository = koin.get(),
                journalNotesRepository = notes,
            )
        retryViewModel.retryRecovery()
        assertEquals(
            SyncIssueRetryFeedback.REQUESTED,
            withTimeout(15_000) { retryViewModel.retryFeedback.first { it != null } },
        )
        awaitQueueDrained(sync)

        assertNull(notes.getNoteById(deletedNoteId), "server retry resurrected a locally deleted note")
        assertEquals(
            "Offline edit survives process restart",
            (notes.getNoteById(offlineNoteId) as? JournalNote.Text)?.content,
        )
        val remote = koin.get<CloudApiClient>().getContentChanges(fixture.accessToken, since = 0L, limit = 100).getOrThrow()
        assertTrue(remote.deletions.any { it.id == deletedNoteId.toString() }, "server did not receive the pending deletion")
        assertTrue(remote.changes.any { it.id == offlineNoteId.toString() }, "server did not receive the offline edit")

        preferences.edit().remove(PREPARED).commit()
    }

    private suspend fun awaitSettled(sync: DefaultSyncManager) {
        withTimeout(120_000) {
            while (true) {
                val result = sync.fullSync()
                val status = sync.getSyncStatus()
                if (result.success && status.pendingUploads == 0 && status.pendingDownloads == 0 && !status.isSyncing) return@withTimeout
                delay(1_000)
            }
        }
    }

    private suspend fun awaitQueueDrained(sync: DefaultSyncManager) {
        withTimeout(120_000) {
            while (true) {
                val status = sync.getSyncStatus()
                if (status.pendingUploads == 0 && status.pendingDownloads == 0 && !status.isSyncing) return@withTimeout
                delay(1_000)
            }
        }
    }

    private fun setNetworkEnabled(device: UiDevice, enabled: Boolean) {
        val mode = if (enabled) "disable" else "enable"
        device.executeShellCommand("cmd connectivity airplane-mode $mode")
        device.executeShellCommand(if (enabled) "svc wifi enable" else "svc wifi disable")
        device.executeShellCommand(if (enabled) "svc data enable" else "svc data disable")
    }

    private suspend fun awaitServer(origin: String): Boolean {
        repeat(30) {
            if (serverReachable(origin)) return true
            delay(500)
        }
        return false
    }

    private suspend fun awaitServerUnavailable(origin: String): Boolean {
        repeat(20) {
            if (!serverReachable(origin)) return true
            delay(500)
        }
        return false
    }

    private fun serverReachable(origin: String): Boolean =
        runCatching {
            val connection = URL("$origin/health").openConnection() as HttpURLConnection
            connection.connectTimeout = 1_000
            connection.readTimeout = 1_000
            try {
                connection.responseCode in 200..499
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)

    private suspend fun fetchFixtureOrSkip(): Fixture? {
        val json =
            runCatching {
                val client = HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis = 5_000 } }
                try {
                    Json.parseToJsonElement(client.get(FIXTURE_URL).bodyAsText()).jsonObject
                } finally {
                    client.close()
                }
            }.getOrNull()
        assumeTrue("isolated persistent-server fixture is not running", json != null)
        val fixture = requireNotNull(json)
        fun field(name: String) = fixture.getValue(name).jsonPrimitive.content
        return Fixture(
            origin = field("origin"),
            accountId = field("owner"),
            accessToken = field("token"),
            refreshToken = field("refreshToken"),
            recoveryWords = field("recovery").split(' '),
        )
    }

    private data class Fixture(
        val origin: String,
        val accountId: String,
        val accessToken: String,
        val refreshToken: String,
        val recoveryWords: List<String>,
    )

    private companion object {
        const val FIXTURE_URL = "http://10.0.2.2:18879/fixture"
        const val PREFERENCES = "sync-process-restart-acceptance"
        const val PREPARED = "prepared"
        const val PREVIOUS_PID = "previous_pid"
        const val ACCOUNT_ID = "account_id"
        const val SERVER_ORIGIN = "server_origin"
        const val JOURNAL_ID = "journal_id"
        const val DELETED_NOTE_ID = "deleted_note_id"
        const val OFFLINE_NOTE_ID = "offline_note_id"
    }
}

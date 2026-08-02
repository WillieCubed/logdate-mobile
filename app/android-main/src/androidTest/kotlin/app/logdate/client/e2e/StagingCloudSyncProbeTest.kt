package app.logdate.client.e2e

import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.DefaultSyncManager
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.koin.core.context.GlobalContext
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Real-client staging probe. Credentials are supplied only through instrumentation arguments.
 *
 * Run this test twice, on two fresh managed devices, with the same arguments:
 * `mode=create` uploads a uniquely marked text entry; `mode=read` downloads it and verifies the
 * marker. The access/refresh tokens are never checked in and should be short-lived disposable
 * credentials created by scripts/passkey-verify/sim.py.
 */
@RunWith(AndroidJUnit4::class)
class StagingCloudSyncProbeTest {
    @Test
    fun syncsARealEntryAcrossFreshManagedDevices() = runBlocking {
        val args = InstrumentationRegistry.getArguments().getString(ARGUMENTS).orEmpty()
        val values = parseArguments(args)
        val mode = values["mode"]
        val marker = values["marker"]
        val accessToken = values["accessToken"]
        val refreshToken = values["refreshToken"]
        val accountId = values["accountId"]
        val serverOrigin = values["serverOrigin"]
        assumeTrue("mode must be create or read", mode == "create" || mode == "read")
        require(!marker.isNullOrBlank()) { "marker is required" }
        require(!accessToken.isNullOrBlank()) { "accessToken is required" }
        require(!refreshToken.isNullOrBlank()) { "refreshToken is required" }
        require(!accountId.isNullOrBlank()) { "accountId is required" }
        require(!serverOrigin.isNullOrBlank()) { "serverOrigin is required" }

        val koin = GlobalContext.get()
        // The debug artifact is built with -Plogdate.backendUrl pointing at this origin.
        // Keeping the origin in the runtime arguments makes accidental prod-token/prod-build
        // mixing visible in the probe output without adding a production-only config hook.
        require(serverOrigin.startsWith("https://")) { "serverOrigin must use HTTPS" }
        koin.get<SessionStorage>().saveSession(UserSession(accessToken, refreshToken, accountId))
        val sync = koin.get<DefaultSyncManager>()
        val notes = koin.get<JournalNotesRepository>()
        val journals = koin.get<JournalRepository>()

        if (mode == "create") {
            val now = Clock.System.now()
            val journal = app.logdate.shared.model.Journal(title = "Staging sync probe $marker")
            journals.create(journal)
            notes.create(
                JournalNote.Text(
                    creationTimestamp = now,
                    lastUpdated = now,
                    content = marker,
                ),
                journal.id,
            )
            val result = sync.fullSync()
            assertTrue(result.success, "create-device full sync failed: ${result.errors}")
        } else {
            val result = sync.fullSync()
            assertTrue(result.success, "read-device full sync failed: ${result.errors}")
            assertTrue(
                notes.allNotesObserved.first().any { note ->
                    note is JournalNote.Text && note.content == marker
                },
                "fresh device did not download marker $marker",
            )
        }
    }

    private fun parseArguments(raw: String): Map<String, String> =
        raw.split('&')
            .asSequence()
            .mapNotNull { item ->
                val separator = item.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                item.substring(0, separator) to item.substring(separator + 1)
            }.toMap()

    private companion object {
        const val ARGUMENTS = "logdate.syncProbeArguments"
    }
}

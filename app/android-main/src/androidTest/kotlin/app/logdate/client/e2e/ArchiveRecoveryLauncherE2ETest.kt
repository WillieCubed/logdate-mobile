package app.logdate.client.e2e

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.feature.core.restore.AndroidRestoreLauncher
import app.logdate.feature.core.restore.RestoreLauncher
import app.logdate.feature.core.restore.RestoreOutcome
import app.logdate.feature.core.restore.RestoreSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class ArchiveRecoveryLauncherE2ETest {
    @Test
    fun completedPartialRecoveryReconnectsWithinTheSameProcess(): Unit =
        runBlocking {
            assumeTrue(InstrumentationRegistry.getArguments().getString("archiveLauncherMode") == "reconnect")
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            require(context.packageName.endsWith(".debug"))
            val label = InstrumentationRegistry.getArguments().getString("archiveRecoveryFixtureLabel") ?: "acceptance"
            require(label.matches(Regex("[a-z0-9-]+")))
            val fixture = JSONObject(context.noBackupFilesDir.resolve("archive-media-recovery-$label.json").readText())
            val record = JSONObject(context.filesDir.resolve("archive_recovery/records/${fixture.getString("recoveryId")}.json").readText())
            val summary = Json.decodeFromString<RestoreSummary>(record.getJSONObject("summary").toString())
            val launcher = GlobalContext.get().get<RestoreLauncher>() as AndroidRestoreLauncher
            val id = UUID.fromString(Uuid.random().toString())
            launcher.beginRestoreWork(id)
            launcher.completeRestoreForWork(id, RestoreOutcome.Success(summary))
            val result = CompletableDeferred<RestoreOutcome>()
            launcher.setRestoreCompletionCallback { result.complete(it) }
            val outcome = withContext(Dispatchers.IO) { withTimeout(5_000) { result.await() } }
            assertEquals(summary.recoveryId, assertIs<RestoreOutcome.Success>(outcome).summary.recoveryId)
            assertEquals(2, outcome.summary.unavailableOriginals)
        }
}

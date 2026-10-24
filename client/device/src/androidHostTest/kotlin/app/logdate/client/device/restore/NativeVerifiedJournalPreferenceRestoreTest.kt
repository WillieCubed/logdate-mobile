package app.logdate.client.device.restore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.datastore.createAndroidMainDataStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class NativeVerifiedJournalPreferenceRestoreTest {
    @Test
    fun `verified journal preferences replace the active DataStore and persist on disk`() =
        runBlocking {
            val directory = createTempDirectory("verified-journal-preferences").toFile()
            val files = File(directory, "files").apply { mkdirs() }
            val context = mockk<Context> { every { filesDir } returns files }
            val original = File(directory, "verified-source.preferences_pb")
            val job = SupervisorJob()
            try {
                val key = stringPreferencesKey("journal.preferred_view")
                val source = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { original }
                source.edit { it[key] = "source-view" }
                job.cancelAndJoin()

                val active = createAndroidMainDataStore(context)
                active.edit { it[key] = "destination-view" }
                restoreVerifiedNativeJournalPreferences(context, original)

                assertEquals("source-view", active.data.first()[key])
                assertContentEquals(original.readBytes(), File(files, "logdate.preferences_pb").readBytes())
            } finally {
                job.cancelAndJoin()
                directory.deleteRecursively()
            }
        }
}

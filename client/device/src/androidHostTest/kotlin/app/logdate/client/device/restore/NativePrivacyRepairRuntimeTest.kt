package app.logdate.client.device.restore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class NativePrivacyRepairRuntimeTest {
    @Test
    fun `isolated reader decodes custody payload without mutating source or primary readers`() =
        runBlocking {
            fixture { context, root ->
                val source = File(root, "custody-payload")
                val staged = File(root, "source.preferences_pb")
                val key = stringPreferencesKey("security_level")
                val job = SupervisorJob()
                try {
                    val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { staged }
                    store.edit { it[key] = "BIOMETRIC" }
                } finally {
                    job.cancelAndJoin()
                }
                staged.copyTo(source)
                val before = source.readBytes()
                repeat(2) { assertEquals("BIOMETRIC", readNativePrivacyRepairPreferences(context, source)[key]) }
                assertContentEquals(before, source.readBytes())
                assertTrue(
                    context.noBackupFilesDir
                        .listFiles()
                        .orEmpty()
                        .isEmpty(),
                )
                assertTrue(File(root, "files").listFiles().orEmpty().isEmpty())
            }
        }

    @Test
    fun `reader refuses empty oversized corrupt and symlink payloads without retaining scratch`() =
        runBlocking {
            fixture { context, root ->
                val source = File(root, "source")
                source.writeBytes(byteArrayOf())
                assertFails { readNativePrivacyRepairPreferences(context, source) }
                java.io.RandomAccessFile(source, "rw").use { it.setLength(4L * 1024 * 1024 + 1) }
                assertFails { readNativePrivacyRepairPreferences(context, source) }
                source.writeText("not protobuf")
                assertFails { readNativePrivacyRepairPreferences(context, source) }
                val link = File(root, "linked")
                Files.createSymbolicLink(link.toPath(), source.toPath())
                assertFails { readNativePrivacyRepairPreferences(context, link) }
                assertTrue(
                    context.noBackupFilesDir
                        .listFiles()
                        .orEmpty()
                        .isEmpty(),
                )
            }
        }

    private suspend fun fixture(block: suspend (Context, File) -> Unit) {
        val root = Files.createTempDirectory("privacy-runtime").toFile()
        val privateRoot = File(root, "private").apply { mkdirs() }
        val files = File(root, "files").apply { mkdirs() }
        val context =
            mockk<Context> {
                every { noBackupFilesDir } returns privateRoot
                every { filesDir } returns files
            }
        try {
            block(context, root)
        } finally {
            root.deleteRecursively()
        }
    }
}

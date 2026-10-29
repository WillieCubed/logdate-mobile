package app.logdate.client.data.restore

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import app.logdate.client.device.restore.NativeJournalReplayCustody
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NativeJournalTransferMediaRoutingTest {
    @Test
    fun `selected original custody does not mean the fresh journal snapshot was staged`() =
        fixture { context ->
            val custody = NativeJournalReplayCustody(context)
            custody.capture("files/audio_notes/take.m4a", 4, "take".byteInputStream())
            assertFalse(NativeJournalTransferStaging(context).wasStaged())
            assertTrue(custody.hasInputs(), "Selected original must remain for proof-bound reconciliation")
        }

    @Test
    fun `an unrelated journal cohort still prevents fresh snapshot installation`() =
        fixture { context ->
            val custody = NativeJournalReplayCustody(context)
            custody.capture("files/audio_notes/take.m4a", 4, "take".byteInputStream())
            custody.capture("database/logdate", 7, "journal".byteInputStream())
            assertTrue(NativeJournalTransferStaging(context).wasStaged())
            assertTrue(custody.hasInputs())
        }

    private fun fixture(test: (Context) -> Unit) {
        val root = Files.createTempDirectory("native-media-routing").toFile()
        val context =
            object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
                override fun getNoBackupFilesDir(): File = File(root, "no-backup").apply { mkdirs() }

                override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            }
        try {
            test(context)
        } finally {
            check(root.deleteRecursively())
        }
    }
}

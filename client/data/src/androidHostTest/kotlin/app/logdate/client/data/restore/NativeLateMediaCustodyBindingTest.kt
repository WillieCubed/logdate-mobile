package app.logdate.client.data.restore

import android.app.Application
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import app.logdate.client.device.restore.NativeJournalReplayCustody
import app.logdate.client.media.storage.UnavailableMediaReferences
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NativeLateMediaCustodyBindingTest {
    @Test
    fun `same-byte sibling cannot acknowledge a callback whose target CAS failed`() =
        runTest {
            val root = Files.createTempDirectory("native-late-source-binding-").toFile()
            try {
                val context =
                    object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
                        override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
                    }
                val custody = NativeJournalReplayCustody(context)
                val bytes = "the same original bytes".encodeToByteArray()
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                val relative = "objects/sha256/${digest.take(2)}/$digest.png"
                val siblingRelative = relative.removeSuffix(".png") + ".retained-1.png"
                val pathA = "files/media/$relative"
                val pathB = "files/media/$siblingRelative"
                val referenceA = "logdate-media://library/$relative"
                val referenceB = "logdate-media://library/$siblingRelative"
                custody.capture(pathA, bytes.size.toLong(), bytes.inputStream())
                custody.capture(pathB, bytes.size.toLong(), bytes.inputStream())
                val published = File(root, "published-original")
                var allowA = false
                var aCommitted = false
                var bCommitted = false
                val targetA =
                    NativeLateMediaRepair.Target(setOf(UnavailableMediaReferences.nativeRestore(referenceA))) {
                        if (allowA) aCommitted = true
                        allowA
                    }
                val targetB =
                    NativeLateMediaRepair.Target(setOf(UnavailableMediaReferences.nativeRestore(referenceB))) {
                        bCommitted = true
                        true
                    }

                fun repair(): NativeLateMediaRepair {
                    val candidates = custody.sealedPayloads().associate { it.path to it.file }
                    return NativeLateMediaRepair(
                        listOf(candidates),
                        requireCurrent = {},
                        available = { it == referenceA && published.isFile },
                        importOriginal = { asset ->
                            asset.file.copyTo(published, overwrite = true)
                            referenceA
                        },
                        authorizeUnreferenced = { _, _ -> true },
                    )
                }

                val first = repair().repair(listOf(targetA, targetB))
                acknowledgeNativeLateMedia(custody, first.recoveredSources, verify = { it == referenceA && published.isFile }) {}

                assertFalse(aCommitted)
                assertTrue(bCommitted)
                assertEquals(listOf(pathA), custody.sealedPayloads().map { it.path })
                assertTrue(first.retry)

                allowA = true
                val resumed = repair().repair(listOf(targetA))
                acknowledgeNativeLateMedia(custody, resumed.recoveredSources, verify = { it == referenceA && published.isFile }) {}

                assertTrue(aCommitted)
                assertFalse(custody.hasInputs())
            } finally {
                check(root.deleteRecursively())
            }
        }
}

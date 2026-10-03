package app.logdate.client.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.feature.core.export.CloudArchiveCipher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CloudArchiveCipherTest : CloudArchiveWorkerFixture() {
    @Test
    fun `archive from another recovery identity cannot reach restore`() =
        runTest {
            val source =
                File(context.cacheDir, "cloud-wrong-identity-source.zip").apply {
                    writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1, 2, 3))
                }
            val encrypted = archiveCipher.encrypt(source)
            val destination = File(context.cacheDir, "cloud-wrong-identity-restored.zip")
            val wrongIdentityCipher = CloudArchiveCipher { ByteArray(32) { 8 } }

            assertTrue(runCatching { wrongIdentityCipher.decrypt(encrypted, destination) }.isFailure)
            assertFalse(destination.exists())
            source.delete()
        }

    @Test
    fun `legacy server encrypted zip backup remains restorable`() =
        runTest {
            val legacyZip = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1, 2, 3)
            val destination = File(context.cacheDir, "cloud-legacy-restore.zip")

            archiveCipher.decrypt(legacyZip, destination)

            assertTrue(destination.readBytes().contentEquals(legacyZip))
            destination.delete()
        }

    @Test
    fun `file backed archive encryption authenticates before returning plaintext`() =
        runTest {
            val source =
                File(context.cacheDir, "cloud-file-cipher-source.zip").apply {
                    writeBytes(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1, 2, 3, 4))
                }
            val encrypted = File(context.cacheDir, "cloud-file-cipher-encrypted.bin")
            val restored = File(context.cacheDir, "cloud-file-cipher-restored.zip")
            val corruptedRestored = File(context.cacheDir, "cloud-file-cipher-corrupted.zip")

            archiveCipher.encrypt(source, encrypted)
            assertEquals("LDCB2", encrypted.inputStream().use { it.readNBytes(5).decodeToString() })
            archiveCipher.decrypt(encrypted, restored)
            assertTrue(restored.readBytes().contentEquals(source.readBytes()))

            val corrupted = encrypted.readBytes().also { bytes -> bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte() }
            encrypted.writeBytes(corrupted)
            assertTrue(runCatching { archiveCipher.decrypt(encrypted, corruptedRestored) }.isFailure)
            assertFalse(corruptedRestored.exists())

            source.delete()
            encrypted.delete()
            restored.delete()
        }
}

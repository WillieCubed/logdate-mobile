package app.logdate.client.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkerParameters
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.domain.export.archive.ArchiveContainer
import app.logdate.client.domain.export.archive.ArchiveCategory
import app.logdate.client.domain.export.archive.ArchiveCounts
import app.logdate.client.domain.export.archive.ArchiveExportProgress
import app.logdate.client.domain.export.archive.ArchiveExportSummary
import app.logdate.client.domain.export.archive.ArchiveOmission
import app.logdate.client.domain.export.archive.ArchiveOmissionReason
import app.logdate.client.domain.export.archive.ArchivePath
import app.logdate.client.domain.export.archive.ArchiveScope
import app.logdate.client.domain.export.archive.ExportArchiveUseCase
import app.logdate.client.sync.cloud.BackupFile
import app.logdate.client.sync.cloud.BackupMetadata
import app.logdate.client.sync.cloud.BackupUploadResult
import app.logdate.client.sync.cloud.BackupUploadFileRequest
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.client.sync.cloud.CloudBackupDataSource
import app.logdate.feature.core.export.CloudArchiveCipher
import app.logdate.feature.core.export.CloudBackupWorker
import app.logdate.feature.core.restore.CloudRestoreWorker
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.io.files.Path
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import okio.buffer
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class CloudArchiveCipherTest : CloudArchiveWorkerFixture() {
    @Test
    fun `archive from another recovery identity cannot reach restore`() = runTest {
        val source = File(context.cacheDir, "cloud-wrong-identity-source.zip").apply {
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
    fun `legacy server encrypted zip backup remains restorable`() = runTest {
        val legacyZip = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1, 2, 3)
        val destination = File(context.cacheDir, "cloud-legacy-restore.zip")

        archiveCipher.decrypt(legacyZip, destination)

        assertTrue(destination.readBytes().contentEquals(legacyZip))
        destination.delete()
    }

    @Test
    fun `file backed archive encryption authenticates before returning plaintext`() = runTest {
        val source = File(context.cacheDir, "cloud-file-cipher-source.zip").apply {
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

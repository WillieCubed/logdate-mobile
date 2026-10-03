@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.sync.cloud.BackupUploadFileRequest
import app.logdate.client.sync.cloud.CloudApiException
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class LargeArchiveStreamingE2ETest {
    @Test
    fun `100 MiB and 1 GiB archives round trip over HTTP below archive-sized heap`() =
        runBlocking {
            assumeTrue(System.getenv("LOGDATE_LARGE_BACKUP_TEST") == "1")
            check(System.getenv("LOGDATE_DIAGNOSTIC_TEST_DISPOSABLE") == "true")
            check(System.getenv("DATABASE_URL").orEmpty().startsWith("jdbc:postgresql://127.0.0.1:"))
            check(!System.getenv("SERVER_ENCRYPTION_KEY").isNullOrBlank())
            assertTrue(Runtime.getRuntime().maxMemory() <= 512L * 1024 * 1024, "proof requires a bounded test-worker heap")
            val temporary = Files.createTempDirectory("archive-http-proof-")
            try {
                withServerClientHarness {
                    val account = apiClient.createAccountWithSyntheticPasskey("archive_${Uuid.random().toString().take(8)}")
                    val token = account.data.tokens.accessToken
                    try {
                        for (size in listOf(100L * 1024 * 1024, 1024L * 1024 * 1024)) {
                            val source = temporary.resolve("source.zip")
                            val restored = temporary.resolve("restored.zip")
                            createStoredArchive(source, size)
                            val uploaded =
                                apiClient
                                    .uploadBackupFile(
                                        token,
                                        BackupUploadFileRequest(
                                            "archive-proof",
                                            "{}",
                                            Path(source.toString()),
                                            size,
                                        ),
                                    ).getOrThrow()
                            val blobRoot =
                                java.nio.file.Path
                                    .of(System.getenv("LOGDATE_BLOB_STORAGE_DIR"))
                            val stored =
                                Files.walk(blobRoot).use { paths ->
                                    paths
                                        .filter {
                                            Files.isRegularFile(
                                                it,
                                            ) &&
                                                it.fileName.toString() == uploaded.id
                                        }.findFirst()
                                        .orElseThrow()
                                }
                            try {
                                Files.newInputStream(stored).use { input ->
                                    assertContentEquals("LDBK1".toByteArray(), input.readNBytes(5))
                                }
                                assertFalse(digest(source).contentEquals(digest(stored)))
                                assertEquals(size, uploaded.sizeBytes)
                                apiClient
                                    .downloadBackupToFile(token, uploaded.id, Path(restored.toString()))
                                    .fold(
                                        onSuccess = {},
                                        onFailure = { failure ->
                                            throw AssertionError(
                                                "Backup download failed for $size bytes (${safeErrorCode(failure)})",
                                            )
                                        },
                                    )
                                assertEquals(size, Files.size(restored))
                                assertContentEquals(digest(source), digest(restored))
                                ZipFile(restored.toFile()).use { zip ->
                                    assertEquals(1, zip.size())
                                    val entry = zip.getEntry(ENTRY_NAME)
                                    assertEquals(size - ZIP_OVERHEAD, entry.size)
                                    zip.getInputStream(entry).use { input ->
                                        val checksum = CRC32()
                                        val chunk = ByteArray(64 * 1024)
                                        var total = 0L
                                        while (true) {
                                            val count = input.read(chunk)
                                            if (count < 0) break
                                            checksum.update(chunk, 0, count)
                                            total += count
                                        }
                                        assertEquals(entry.size, total)
                                        assertEquals(entry.crc, checksum.value)
                                    }
                                }
                            } finally {
                                assertTrue(apiClient.deleteBackup(token, uploaded.id).isSuccess)
                                assertFalse(Files.exists(stored))
                                Files.deleteIfExists(source)
                                Files.deleteIfExists(restored)
                            }
                        }
                    } finally {
                        assertEquals(HttpStatusCode.NoContent, httpClient.delete("$baseUrl/auth/me") { bearerAuth(token) }.status)
                        assertTrue(
                            httpClient.get("$baseUrl/auth/me") { bearerAuth(token) }.status in
                                listOf(HttpStatusCode.Unauthorized, HttpStatusCode.NotFound),
                        )
                    }
                }
            } finally {
                Files.list(temporary).use { paths -> paths.forEach(Files::deleteIfExists) }
                Files.deleteIfExists(temporary)
            }
        }

    private fun createStoredArchive(
        path: java.nio.file.Path,
        size: Long,
    ) {
        val chunk = ByteArray(64 * 1024) { (it * 31).toByte() }
        val payloadSize = size - ZIP_OVERHEAD
        val checksum = CRC32()
        var remaining = payloadSize
        while (remaining > 0) {
            val count = minOf(remaining, chunk.size.toLong()).toInt()
            checksum.update(chunk, 0, count)
            remaining -= count
        }
        ZipOutputStream(Files.newOutputStream(path).buffered()).use { zip ->
            zip.putNextEntry(
                ZipEntry(ENTRY_NAME).apply {
                    method = ZipEntry.STORED
                    this.size = payloadSize
                    compressedSize = payloadSize
                    this.crc = checksum.value
                    setTimeLocal(LocalDateTime.of(2000, 1, 1, 0, 0))
                },
            )
            remaining = payloadSize
            while (remaining > 0) {
                val count = minOf(remaining, chunk.size.toLong()).toInt()
                zip.write(chunk, 0, count)
                remaining -= count
            }
            zip.closeEntry()
        }
        assertEquals(size, Files.size(path))
    }

    private fun digest(path: java.nio.file.Path): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).buffered().use { input ->
            val chunk = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                digest.update(chunk, 0, count)
            }
        }
        return digest.digest()
    }

    private fun safeErrorCode(failure: Throwable): String =
        (failure as? CloudApiException)
            ?.errorCode
            ?.takeIf { code ->
                code.length in 1..64 && code.all { it in 'A'..'Z' || it in '0'..'9' || it == '_' }
            }
            ?: "UNKNOWN"

    private companion object {
        const val ENTRY_NAME = "data.bin"
        const val ZIP_OVERHEAD = 114L
    }
}

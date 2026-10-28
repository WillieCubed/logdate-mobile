@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.media.MediaFileSource
import app.logdate.client.sync.cloud.CloudApiClient
import app.logdate.client.sync.cloud.DefaultCloudMediaDataSource
import app.logdate.client.sync.cloud.MediaUpload
import app.logdate.client.sync.cloud.MediaUploadResponse
import app.logdate.client.sync.crypto.AesGcmMediaPayloadCrypto
import app.logdate.client.sync.metadata.MediaUploadIntentStore
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class MediaUploadReceiptClientServerE2ETest {
    @Test
    fun `real HTTP accepted encrypted media is recovered without another payload after lost acknowledgement`() =
        runTest {
            withServerClientHarness {
                val account = apiClient.createAccountWithSyntheticPasskey("receipt_" + Uuid.random().toString().take(8))
                val token = account.data.tokens.accessToken
                val scope =
                    UploadScope(
                        account.data.account.id
                            .toString(),
                        baseUrl,
                    )
                val storage = InMemoryKeyValues()
                val sent = AtomicLong(0)
                var loseResponse = true
                val actual = apiClient
                val boundary =
                    object : CloudApiClient by actual {
                        override suspend fun uploadMedia(
                            accessToken: String,
                            media: MediaUpload,
                        ): Result<MediaUploadResponse> {
                            val counted =
                                MediaUpload(
                                    media.contentId,
                                    media.fileName,
                                    media.mimeType,
                                    media.sizeBytes,
                                    media.deviceId,
                                    uploadIntentId = media.uploadIntentId,
                                ) {
                                    object : RawSource {
                                        val source = media.openBody()

                                        override fun readAtMostTo(
                                            sink: Buffer,
                                            byteCount: Long,
                                        ): Long {
                                            val read = source.readAtMostTo(sink, byteCount)
                                            if (read > 0) sent.addAndGet(read)
                                            return read
                                        }

                                        override fun close() {
                                            source.close()
                                        }
                                    }
                                }
                            val committed = actual.uploadMedia(accessToken, counted)
                            if (committed.isSuccess && loseResponse) {
                                loseResponse = false
                                return Result.failure(IOException("Synthetic acknowledgement loss after real server commit"))
                            }
                            return committed
                        }
                    }
                val key = ByteArray(32) { (it + 1).toByte() }
                val original = ByteArray(128 * 1024) { (it % 251).toByte() }
                val file = MediaFileSource("synthetic.jpg", "image/jpeg", original.size.toLong()) { Buffer().apply { write(original) } }
                val content = Uuid.random()

                fun client() =
                    DefaultCloudMediaDataSource(
                        boundary,
                        AesGcmMediaPayloadCrypto(key),
                        MediaUploadIntentStore(storage) { scope },
                    )
                assertTrue(client().uploadMedia(token, content, file).isFailure)
                val firstBytes = sent.get()
                assertTrue(firstBytes > original.size, "Actual HTTP body includes encrypted framing")
                val recovered = client().uploadMedia(token, content, file).getOrThrow()
                assertEquals(firstBytes, sent.get(), "Receipt recovery sends no further payload bytes")
                assertContentEquals(original, client().downloadMedia(token, recovered.mediaId).getOrThrow().data)
            }
        }
}

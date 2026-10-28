@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package app.logdate.integration.e2e.journeys

import app.logdate.client.media.MediaFileSource
import app.logdate.client.sync.crypto.AesGcmMediaPayloadCrypto
import app.logdate.client.sync.metadata.MediaUploadIntentStore
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.space.SpaceDataHomes
import app.logdate.client.sync.space.SpaceLocation
import app.logdate.client.sync.space.SpaceMediaDataSource
import app.logdate.integration.e2e.fixtures.createAccountWithSyntheticPasskey
import app.logdate.integration.e2e.harness.withServerClientHarness
import app.logdate.space.BlobUploadReceiptLookup
import app.logdate.space.BlobUploadReceipts
import app.logdate.space.DataHome
import app.logdate.space.SpaceRef
import app.logdate.space.XrpcDataHome
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import studio.hypertext.atproto.xrpc.BearerToken
import studio.hypertext.atproto.xrpc.KtorXrpcClient
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PersonalSpaceBlobReceiptE2ETest {
    @Test
    fun `real personal space HTTP recovers committed ciphertext without sending another body`() =
        runTest {
            withServerClientHarness {
                val account = apiClient.createAccountWithSyntheticPasskey("space_receipt_" + Uuid.random().toString().take(8))
                val token = account.data.tokens.accessToken
                val origin = baseUrl.removeSuffix("/api/v1")
                val space = SpaceRef.personal(requireNotNull(account.data.account.did))
                val actual = XrpcDataHome(KtorXrpcClient(httpClient, origin), httpClient, origin) { BearerToken(token) }
                val sent = AtomicLong(0)
                var loseResponse = true
                val boundary: DataHome =
                    object : DataHome by actual, BlobUploadReceipts {
                        override suspend fun lookupBlobUpload(
                            space: SpaceRef,
                            intent: String,
                        ): BlobUploadReceiptLookup = actual.lookupBlobUpload(space, intent)

                        override suspend fun uploadBlobWithIntent(
                            space: SpaceRef,
                            intent: String,
                            sizeBytes: Long,
                            open: () -> RawSource,
                        ): String {
                            val cid =
                                actual.uploadBlobWithIntent(space, intent, sizeBytes) {
                                    object : RawSource {
                                        val source = open()

                                        override fun readAtMostTo(
                                            sink: Buffer,
                                            byteCount: Long,
                                        ): Long {
                                            val count = source.readAtMostTo(sink, byteCount)
                                            if (count > 0) sent.addAndGet(count)
                                            return count
                                        }

                                        override fun close() = source.close()
                                    }
                                }
                            if (loseResponse) {
                                loseResponse = false
                                throw IOException("synthetic response lost after real HTTP commit")
                            }
                            return cid
                        }
                    }
                val storage = InMemoryKeyValues()
                val scope =
                    UploadScope(
                        account.data.account.id
                            .toString(),
                        origin,
                    )

                fun source() =
                    SpaceMediaDataSource(
                        SpaceDataHomes { _, _ -> boundary },
                        AesGcmMediaPayloadCrypto(ByteArray(32) { 7 }),
                        { SpaceLocation(origin, space) },
                        intents = MediaUploadIntentStore(storage) { scope },
                    )
                val bytes = ByteArray(4096) { 13 }
                val file = MediaFileSource("synthetic-space.jpg", "image/jpeg", bytes.size.toLong()) { Buffer().apply { write(bytes) } }
                val id = Uuid.random()
                val failed = source().uploadMedia(token, id, file)
                assertTrue(failed.isFailure)
                assertTrue(
                    failed.exceptionOrNull()?.message?.contains("synthetic response lost") == true,
                    failed.exceptionOrNull().toString(),
                )
                val firstBytes = sent.get()
                assertEquals(4144L, firstBytes)
                val recovered = source().uploadMedia(token, id, file).getOrThrow()
                assertEquals(firstBytes, sent.get())
                assertContentEquals(
                    bytes,
                    source().downloadReferencedMedia(token, recovered.downloadUrl, recovered.mediaId).getOrThrow().data,
                )
            }
        }
}

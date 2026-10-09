package app.logdate.client.sync.cloud

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.client.repository.transcription.TranscriptDocumentStatus
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.test.FakeCloudApiClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class EncryptedTranscriptSyncTest {
    @Test
    fun `structured transcript is encrypted separately from caption and round trips`() =
        runTest {
            val crypto = TestCryptoManager()
            val identity = IdentityKeyManager(InMemorySecureStorage(), crypto)
            identity.setupNewIdentity()
            val cipher = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)
            val api = FakeCloudApiClient()
            val source = DefaultCloudContentDataSource(api, cipher)
            val id = Uuid.random()
            val now = Clock.System.now()
            val document = TranscriptDocument.fromPlainText("Private spoken words").copy(revision = 7)
            val note =
                JournalNote.Audio(
                    "audio.m4a",
                    uid = id,
                    creationTimestamp = now,
                    lastUpdated = now,
                    caption = "My caption",
                    transcript = document,
                )
            assertTrue(source.uploadNote("token", note).isSuccess)
            assertTrue(source.updateNote("token", note).isSuccess)
            for (payload in listOf(
                api.uploadContentCalls
                    .single()
                    .second.transcript,
                api.updateContentCalls
                    .single()
                    .third.transcript,
            )) {
                assertTrue(payload.orEmpty().startsWith("LDSE2:"))
                assertFalse(payload.orEmpty().contains(document.plainText))
                assertEquals(
                    document,
                    Json.decodeFromString<TranscriptDocument>(cipher.decryptString("sync:note:$id:transcript", payload.orEmpty())),
                )
            }
            val upload = api.uploadContentCalls.single().second
            api.getContentChangesResponse =
                Result.success(
                    ContentChangesResponse(
                        changes =
                            listOf(
                                ContentChange(
                                    id.toString(),
                                    "AUDIO",
                                    mediaUri = "audio.m4a",
                                    createdAt = 1,
                                    lastUpdated = 2,
                                    serverVersion = 3,
                                    caption = upload.caption,
                                    transcript = upload.transcript,
                                ),
                            ),
                        deletions = emptyList(),
                        lastTimestamp = 3,
                    ),
                )
            val downloaded =
                source
                    .getContentChanges("token", now)
                    .getOrThrow()
                    .changes
                    .single() as JournalNote.Audio
            assertEquals(document, downloaded.transcript)
            assertEquals("My caption", downloaded.caption)
        }

    @Test
    fun `empty final document survives the wire as a terminal transcript`() =
        runTest {
            val api = FakeCloudApiClient()
            val source = DefaultCloudContentDataSource(api)
            val now = Clock.System.now()
            val document = TranscriptDocument(status = TranscriptDocumentStatus.FINAL)
            val note = JournalNote.Audio("silent.m4a", creationTimestamp = now, lastUpdated = now, transcript = document)
            source.uploadNote("token", note).getOrThrow()
            assertEquals(
                document,
                Json.decodeFromString<TranscriptDocument>(
                    api.uploadContentCalls
                        .single()
                        .second.transcript
                        .orEmpty(),
                ),
            )
        }
}

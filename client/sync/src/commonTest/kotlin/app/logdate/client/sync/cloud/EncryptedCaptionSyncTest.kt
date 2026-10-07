package app.logdate.client.sync.cloud

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.test.FakeCloudApiClient
import app.logdate.shared.model.diagnostics.DiagnosticReason
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class EncryptedCaptionSyncTest {
    @Test
    fun `media captions are encrypted before create and update`() =
        runTest {
            val manager = TestCryptoManager()
            val identity = IdentityKeyManager(InMemorySecureStorage(), manager)
            identity.setupNewIdentity()
            val cipher = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(manager), manager), identity, manager)
            val api = FakeCloudApiClient()
            val source = DefaultCloudContentDataSource(api, cipher)
            val now = Clock.System.now()
            val id = Uuid.random()
            val caption = "Private caption\nwith Unicode ☀️"
            val photo = JournalNote.Image(id, now, now, mediaRef = "https://server/media/photo", caption = caption)
            val video = JournalNote.Video(id, now, now, mediaRef = "https://server/media/video", caption = caption)
            val audio =
                JournalNote.Audio(
                    uid = id,
                    creationTimestamp = now,
                    lastUpdated = now,
                    mediaRef = "https://server/media/audio",
                    caption = caption,
                )
            for (note in listOf(photo, video, audio)) {
                assertTrue(source.uploadNote("token", note).isSuccess)
                assertTrue(source.updateNote("token", note).isSuccess)
            }
            val captions = api.uploadContentCalls.map { it.second.caption } + api.updateContentCalls.map { it.third.caption }
            for (payload in captions) {
                assertTrue(payload.orEmpty().startsWith("LDSE2:"))
                assertFalse(payload.orEmpty().contains(caption))
                assertEquals(caption, cipher.decryptString("sync:note:$id:caption", payload.orEmpty()))
            }
        }

    @Test
    fun `download decrypts captions and retains legacy plaintext compatibility`() =
        runTest {
            val manager = TestCryptoManager()
            val identity = IdentityKeyManager(InMemorySecureStorage(), manager)
            identity.setupNewIdentity()
            val cipher = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(manager), manager), identity, manager)
            val api = FakeCloudApiClient()
            val source = DefaultCloudContentDataSource(api, cipher)
            val id = Uuid.random()
            val value = cipher.encryptString("sync:note:$id:caption", "Protected caption")
            api.getContentChangesResponse =
                Result.success(
                    ContentChangesResponse(
                        changes =
                            listOf(
                                ContentChange(
                                    id = id.toString(),
                                    type = "IMAGE",
                                    mediaUri = "https://server/media/photo",
                                    createdAt = 1,
                                    lastUpdated = 2,
                                    serverVersion = 3,
                                    caption = value,
                                ),
                                ContentChange(
                                    id = id.toString(),
                                    type = "AUDIO",
                                    mediaUri = "https://server/media/audio",
                                    createdAt = 1,
                                    lastUpdated = 2,
                                    serverVersion = 4,
                                    caption = value,
                                ),
                                ContentChange(
                                    id = Uuid.random().toString(),
                                    type = "VIDEO",
                                    mediaUri = "https://server/media/video",
                                    createdAt = 1,
                                    lastUpdated = 2,
                                    serverVersion = 4,
                                    caption = "Legacy caption",
                                ),
                            ),
                        deletions = emptyList(),
                        lastTimestamp = 4,
                    ),
                )
            val result = source.getContentChanges("token", since = Clock.System.now())
            val notes = result.getOrThrow().changes
            assertEquals("Protected caption", (notes[0] as JournalNote.Image).caption)
            assertEquals("Protected caption", (notes[1] as JournalNote.Audio).caption)
            assertEquals("Legacy caption", (notes[2] as JournalNote.Video).caption)
        }

    @Test
    fun `invalid captions report versioned failures without blocking readable entries`() =
        runTest {
            val crypto = TestCryptoManager()
            val identity = IdentityKeyManager(InMemorySecureStorage(), crypto)
            identity.setupNewIdentity()
            val cipher = SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(crypto), crypto), identity, crypto)
            val api = FakeCloudApiClient()
            val wrongField = Uuid.random()
            val corrupt = Uuid.random()
            val readable = Uuid.random()
            api.getContentChangesResponse =
                Result.success(
                    ContentChangesResponse(
                        changes =
                            listOf(
                                ContentChange(
                                    id = wrongField.toString(),
                                    type = "IMAGE",
                                    createdAt = 1,
                                    lastUpdated = 2,
                                    serverVersion = 5,
                                    caption = cipher.encryptString("sync:note:$wrongField:text", "Private caption"),
                                ),
                                ContentChange(
                                    id = corrupt.toString(),
                                    type = "VIDEO",
                                    createdAt = 1,
                                    lastUpdated = 2,
                                    serverVersion = 6,
                                    caption = "LDSE2:invalid",
                                ),
                                ContentChange(
                                    id = readable.toString(),
                                    type = "IMAGE",
                                    createdAt = 1,
                                    lastUpdated = 2,
                                    serverVersion = 7,
                                    caption = "Legacy caption",
                                ),
                            ),
                        deletions = emptyList(),
                        lastTimestamp = 7,
                    ),
                )
            val result = DefaultCloudContentDataSource(api, cipher).getContentChanges("token", Clock.System.now()).getOrThrow()
            assertEquals(listOf(readable), result.changes.map { it.uid })
            assertTrue(result.unreadable.isEmpty())
            assertEquals(
                mapOf(wrongField.toString() to 5L, corrupt.toString() to 6L),
                result.failures.associate { it.entityId to it.serverVersion },
            )
            assertTrue(result.failures.all { it.reason == DiagnosticReason.CORRUPT_PAYLOAD })
            assertEquals(7L, result.lastSyncTimestamp.toEpochMilliseconds())
        }
}

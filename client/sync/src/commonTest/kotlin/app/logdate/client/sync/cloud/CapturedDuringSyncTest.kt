package app.logdate.client.sync.cloud

import app.logdate.client.device.crypto.ContentEncryptionService
import app.logdate.client.device.crypto.IdentityKeyManager
import app.logdate.client.device.crypto.KeyDerivation
import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.sync.crypto.SyncPayloadCipher
import app.logdate.client.sync.test.FakeCloudApiClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * What the person was doing at capture travels to the server encrypted, like a caption: the server must
 * not learn which entries were captured while moving or in a vehicle.
 */
class CapturedDuringSyncTest {
    private val api = FakeCloudApiClient()

    private suspend fun cipher(): SyncPayloadCipher {
        val manager = TestCryptoManager()
        val identity = IdentityKeyManager(InMemorySecureStorage(), manager)
        identity.setupNewIdentity()
        return SyncPayloadCipher(ContentEncryptionService(identity, KeyDerivation(manager), manager), identity, manager)
    }

    @Test
    fun `the capture activity is encrypted before create and update`() =
        runTest {
            val cipher = cipher()
            val source = DefaultCloudContentDataSource(api, cipher)
            val id = Uuid.random()
            val now = Clock.System.now()
            val note =
                JournalNote.Audio(
                    uid = id,
                    mediaRef = "https://server/media/audio",
                    creationTimestamp = now,
                    lastUpdated = now,
                    capturedDuring = CaptureActivity.MOVING,
                )

            assertTrue(source.uploadNote("token", note).isSuccess)
            assertTrue(source.updateNote("token", note).isSuccess)

            val payloads = api.uploadContentCalls.map { it.second.capturedDuring } + api.updateContentCalls.map { it.third.capturedDuring }
            assertEquals(2, payloads.size)
            payloads.forEach { payload ->
                assertTrue(payload.orEmpty().startsWith("LDSE2:"))
                assertFalse(payload.orEmpty().contains("moving"))
                assertEquals("moving", cipher.decryptString("sync:note:$id:capturedDuring", payload.orEmpty()))
            }
        }

    @Test
    fun `a note without an activity sends none`() =
        runTest {
            val source = DefaultCloudContentDataSource(api, cipher())
            val now = Clock.System.now()

            source.uploadNote("token", JournalNote.Text(creationTimestamp = now, lastUpdated = now, content = "Typed"))

            assertNull(
                api.uploadContentCalls
                    .single()
                    .second.capturedDuring,
            )
        }

    @Test
    fun `a downloaded activity is decrypted`() =
        runTest {
            val cipher = cipher()
            val id = Uuid.random()
            api.getContentChangesResponse = changes(id, cipher.encryptString("sync:note:$id:capturedDuring", "moving"))

            val note =
                DefaultCloudContentDataSource(api, cipher)
                    .getContentChanges("token", Clock.System.now())
                    .getOrThrow()
                    .changes
                    .single()

            assertEquals(CaptureActivity.MOVING, note.capturedDuring)
        }

    @Test
    fun `an activity a newer version wrote downloads as unknown`() =
        runTest {
            val cipher = cipher()
            val id = Uuid.random()
            api.getContentChangesResponse = changes(id, cipher.encryptString("sync:note:$id:capturedDuring", "swimming"))

            val result = DefaultCloudContentDataSource(api, cipher).getContentChanges("token", Clock.System.now()).getOrThrow()

            assertEquals(listOf(id), result.changes.map { it.uid })
            assertNull(result.changes.single().capturedDuring)
        }

    @Test
    fun `an entry from a server without the field downloads with no activity`() =
        runTest {
            val id = Uuid.random()
            api.getContentChangesResponse = changes(id, capturedDuring = null)

            val note =
                DefaultCloudContentDataSource(api, cipher())
                    .getContentChanges("token", Clock.System.now())
                    .getOrThrow()
                    .changes
                    .single()

            assertNull(note.capturedDuring)
        }

    private fun changes(
        id: Uuid,
        capturedDuring: String?,
    ) = Result.success(
        ContentChangesResponse(
            changes =
                listOf(
                    ContentChange(
                        id = id.toString(),
                        type = "AUDIO",
                        mediaUri = "https://server/media/audio",
                        createdAt = 1,
                        lastUpdated = 2,
                        serverVersion = 3,
                        capturedDuring = capturedDuring,
                    ),
                ),
            deletions = emptyList(),
            lastTimestamp = 3,
        ),
    )
}

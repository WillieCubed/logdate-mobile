package app.logdate.client.sync.metadata

import app.logdate.client.sync.InMemoryKeyValueStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class MediaSyncRefOriginScopingTest {
    private val serverA = "https://cloud.logdate.app"
    private val serverB = "https://journal.example.com"
    private val noteId = Uuid.random()

    @Test
    fun `a reference is reused on the server it was made on`() =
        runTest {
            var origin = serverA
            val store = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), currentOrigin = { origin })

            store.upsert(ref())

            assertEquals("https://cloud.logdate.app/media/a1", store.get(noteId)?.remoteUrl)
        }

    @Test
    fun `a reference made on another server is not reused`() =
        runTest {
            var origin = serverA
            val store = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), currentOrigin = { origin })
            store.upsert(ref())

            origin = serverB

            assertNull(store.get(noteId))
        }

    @Test
    fun `references saved before servers were recorded belong to the first server that reads them`() =
        runTest {
            var origin = serverA
            val storage = InMemoryKeyValueStorage().apply { values["sync_media_ref_$noteId"] = legacyJson() }
            val store = KeyValueMediaSyncRefStore(storage, currentOrigin = { origin })

            assertEquals("https://cloud.logdate.app/media/a1", store.get(noteId)?.remoteUrl)

            origin = serverB
            assertNull(store.get(noteId))
        }

    @Test
    fun `claiming unscoped references ties them to a server before switching away from it`() =
        runTest {
            var origin = serverA
            val storage = InMemoryKeyValueStorage().apply { values["sync_media_ref_$noteId"] = legacyJson() }
            val store = KeyValueMediaSyncRefStore(storage, currentOrigin = { origin })

            store.claimUnscopedRefs(serverA)
            origin = serverB

            assertNull(store.get(noteId))
        }

    @Test
    fun `late captured reference keeps its original owner and never replaces another account cache`() =
        runTest {
            var owner = "owner-b"
            val store = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), { serverA }, { owner })
            store.upsert(ref().copy(remoteUrl = "https://fixture/b"))
            store.upsert(ref().copy(serverOrigin = serverA, ownerId = "owner-a"))
            assertEquals("https://fixture/b", store.get(noteId)?.remoteUrl)
            owner = "owner-a"
            assertEquals(ref().remoteUrl, store.get(noteId)?.remoteUrl)
        }

    @Test
    fun `late draft asset keeps captured origin instead of adopting selected destination`() =
        runTest {
            var origin = serverB
            val store = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), { origin }, { "owner" })
            val draft = Uuid.random()
            val block = Uuid.random()
            store.upsertDraftAsset(draft, block, "image", ref().copy(serverOrigin = serverA, ownerId = "owner"))
            assertNull(store.getDraftAsset(draft, block, "image"))
            origin = serverA
            assertEquals(ref().remoteUrl, store.getDraftAsset(draft, block, "image")?.remoteUrl)
        }

    @Test
    fun `late scoped deletion cannot remove the selected account mapping`() =
        runTest {
            val store = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), { serverB }, { "owner-b" })
            store.upsert(ref())
            store.deleteScoped(noteId, UploadScope("owner-a", serverA))
            assertEquals(ref().remoteUrl, store.get(noteId)?.remoteUrl)
        }

    @Test
    fun `request binding supplies media provenance when upload resumes on another account`() =
        runTest {
            var origin = serverB
            var owner = "owner-b"
            val store = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), { origin }, { owner })
            val binding =
                app.logdate.client.sync.cloud.CloudRequestBinding(
                    app.logdate.client.sync.cloud
                        .CloudRequestLocation(serverA, "$serverA/api/v1"),
                    app.logdate.client.datastore
                        .UserSession("fixture-access", "fixture-refresh", "owner-a"),
                )
            kotlinx.coroutines.withContext(binding) { store.upsert(ref()) }
            assertNull(store.get(noteId))
            origin = serverA
            owner = "owner-a"
            assertEquals(ref().remoteUrl, store.get(noteId)?.remoteUrl)
        }

    private fun ref() =
        MediaSyncRef(
            noteId = noteId.toString(),
            localUri = "file:///media/a1.jpg",
            remoteUrl = "https://cloud.logdate.app/media/a1",
            mediaId = "a1",
            updatedAt = 0,
        )

    private fun legacyJson() =
        """{"noteId":"$noteId","localUri":"file:///media/a1.jpg","remoteUrl":"https://cloud.logdate.app/media/a1",""" +
            """"mediaId":"a1","updatedAt":0}"""
}

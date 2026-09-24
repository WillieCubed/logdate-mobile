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

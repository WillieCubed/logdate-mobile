package app.logdate.client.sync.metadata

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
            val store = KeyValueMediaSyncRefStore(StringStorage(), currentOrigin = { origin })

            store.upsert(ref())

            assertEquals("https://cloud.logdate.app/media/a1", store.get(noteId)?.remoteUrl)
        }

    @Test
    fun `a reference made on another server is not reused`() =
        runTest {
            var origin = serverA
            val store = KeyValueMediaSyncRefStore(StringStorage(), currentOrigin = { origin })
            store.upsert(ref())

            origin = serverB

            assertNull(store.get(noteId))
        }

    @Test
    fun `references saved before servers were recorded belong to the first server that reads them`() =
        runTest {
            var origin = serverA
            val storage = StringStorage().apply { strings["sync_media_ref_$noteId"] = legacyJson() }
            val store = KeyValueMediaSyncRefStore(storage, currentOrigin = { origin })

            assertEquals("https://cloud.logdate.app/media/a1", store.get(noteId)?.remoteUrl)

            origin = serverB
            assertNull(store.get(noteId))
        }

    @Test
    fun `claiming unscoped references ties them to a server before switching away from it`() =
        runTest {
            var origin = serverA
            val storage = StringStorage().apply { strings["sync_media_ref_$noteId"] = legacyJson() }
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

    /** Only the string operations the media reference store uses. */
    private class StringStorage : KeyValueStorage {
        val strings = mutableMapOf<String, String>()

        override suspend fun getString(key: String): String? = strings[key]

        override fun getStringSync(key: String): String? = strings[key]

        override suspend fun putString(
            key: String,
            value: String,
        ) {
            strings[key] = value
        }

        override suspend fun remove(key: String) {
            strings.remove(key)
        }

        override suspend fun contains(key: String): Boolean = key in strings

        override suspend fun clear() = strings.clear()

        override fun observeString(key: String): Flow<String?> = MutableStateFlow(strings[key])

        override suspend fun getBoolean(
            key: String,
            defaultValue: Boolean,
        ): Boolean = error("unused")

        override suspend fun putBoolean(
            key: String,
            value: Boolean,
        ) = error("unused")

        override suspend fun getInt(
            key: String,
            defaultValue: Int,
        ): Int = error("unused")

        override suspend fun putInt(
            key: String,
            value: Int,
        ) = error("unused")

        override suspend fun getLong(
            key: String,
            defaultValue: Long,
        ): Long = error("unused")

        override suspend fun putLong(
            key: String,
            value: Long,
        ) = error("unused")

        override suspend fun getFloat(
            key: String,
            defaultValue: Float,
        ): Float = error("unused")

        override suspend fun putFloat(
            key: String,
            value: Float,
        ) = error("unused")

        override fun observeBoolean(
            key: String,
            defaultValue: Boolean,
        ): Flow<Boolean> = error("unused")

        override fun observeInt(
            key: String,
            defaultValue: Int,
        ): Flow<Int> = error("unused")

        override fun observeLong(
            key: String,
            defaultValue: Long,
        ): Flow<Long> = error("unused")

        override fun observeFloat(
            key: String,
            defaultValue: Float,
        ): Flow<Float> = error("unused")
    }
}

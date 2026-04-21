package studio.hypertext.atproto.repo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.AtUri
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage-focused unit tests for repository models, exceptions, and store interfaces.
 *
 * These tests ensure that repository types maintain expected default behaviors across
 * their public APIs, that exceptions correctly preserve context, and that interface
 * default arguments are properly forwarded to implementations.
 */
class RepoCoverageTest {
    private val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
    private val collection = Nsid.require("studio.hypertext.logdate.content")
    private val recordKey = RecordKey.require("entry-1")
    private val value =
        buildJsonObject {
            put("type", "TEXT")
        }

    @Test
    fun `repo models expose default constructors and serializer companions`() {
        val record =
            RepoRecord(uri = AtUri.require("at://did:plc:ewvi7nxzyoun6zhxrhs64oiz/studio.hypertext.logdate.content/entry-1"), value = value)
        val page = RepoListPage(records = listOf(record))
        val writeResult = RepoWriteResult(uri = record.uri, cid = "cid-1")
        val encodedPage = Json.encodeToString(RepoListPage.serializer(), page)

        assertNull(record.cid)
        assertNull(page.cursor)
        assertEquals(RepoValidationStatus.UNKNOWN, writeResult.validationStatus)
        assertTrue(encodedPage.contains("\"records\""))
        assertEquals(50, RepoRecordStore.DEFAULT_PAGE_SIZE)
    }

    @Test
    fun `repo exceptions preserve their input values and messages`() {
        val unsupported = UnsupportedCollectionException("studio.hypertext.logdate.private")
        val invalidCursor = InvalidRepoCursorException("cursor-1")

        assertEquals("studio.hypertext.logdate.private", unsupported.collection)
        assertTrue(unsupported.message!!.contains("Unsupported AT Protocol collection"))
        assertEquals("cursor-1", invalidCursor.cursor)
        assertTrue(invalidCursor.message!!.contains("Invalid AT Protocol repo cursor"))
    }

    @Test
    fun `repo record store kotlin default arguments forward expected values`() {
        val store = RecordingRepoRecordStore()
        val recordId = RepoRecordId(repo = repo, collection = collection, recordKey = recordKey)

        runSuspend { store.listRecords(repo, collection).getOrThrow() }
        runSuspend { store.createRecord(repo, collection, value).getOrThrow() }
        runSuspend { store.putRecord(recordId, value).getOrThrow() }
        runSuspend { store.deleteRecord(recordId).getOrThrow() }

        assertEquals(RepoRecordStore.DEFAULT_PAGE_SIZE, store.listLimit)
        assertNull(store.listCursor)
        assertEquals(false, store.listReverse)
        assertNull(store.createdRecordKey)
        assertNull(store.putSwapRecord)
        assertNull(store.deleteSwapRecord)
    }

    /**
     * A mock implementation of [RepoRecordStore] that captures invocation arguments for verification.
     */
    private class RecordingRepoRecordStore : RepoRecordStore {
        var listLimit: Int? = null
        var listCursor: String? = "unset"
        var listReverse: Boolean? = null
        var createdRecordKey: RecordKey? = RecordKey.require("placeholder")
        var putSwapRecord: String? = "unset"
        var deleteSwapRecord: String? = "unset"

        override suspend fun getRecord(recordId: RepoRecordId): Result<RepoRecord?> = Result.success(null)

        override suspend fun listRecords(
            repo: AtprotoDid,
            collection: Nsid,
            limit: Int,
            cursor: String?,
            reverse: Boolean,
        ): Result<RepoListPage> {
            listLimit = limit
            listCursor = cursor
            listReverse = reverse
            return Result.success(RepoListPage(emptyList()))
        }

        override suspend fun createRecord(
            repo: AtprotoDid,
            collection: Nsid,
            value: kotlinx.serialization.json.JsonObject,
            recordKey: RecordKey?,
        ): Result<RepoWriteResult> {
            createdRecordKey = recordKey
            return Result.success(
                RepoWriteResult(
                    uri = AtUri.require("at://$repo/$collection/entry-1"),
                    cid = "cid-1",
                ),
            )
        }

        override suspend fun putRecord(
            recordId: RepoRecordId,
            value: kotlinx.serialization.json.JsonObject,
            swapRecord: String?,
        ): Result<RepoWriteResult> {
            putSwapRecord = swapRecord
            return Result.success(RepoWriteResult(uri = recordId.uri, cid = "cid-2"))
        }

        override suspend fun deleteRecord(
            recordId: RepoRecordId,
            swapRecord: String?,
        ): Result<Boolean> {
            deleteSwapRecord = swapRecord
            return Result.success(true)
        }
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>) {
                    outcome = result
                }
            },
        )
        return outcome!!.getOrThrow()
    }
}

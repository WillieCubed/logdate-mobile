package studio.hypertext.atproto.pds

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.DidDocument
import studio.hypertext.atproto.repo.RepoListPage
import studio.hypertext.atproto.repo.RepoRecord
import studio.hypertext.atproto.syntax.AtUri
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdsModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `discovery and repo models serialize as wire payloads`() {
        val repoDid = AtprotoDid.require("did:web:alice.logdate.app")
        val repoDocument = DidDocument(id = repoDid)
        val describeRepo =
            DescribeRepoResponse(
                handle = "alice.logdate.app",
                did = repoDid,
                didDoc = repoDocument,
                collections = listOf(Nsid.require("com.atproto.repo.createRecord")),
                handleIsCorrect = true,
            )
        val page =
            RepoListPage(
                records =
                    listOf(
                        RepoRecord(
                            uri = AtUri.require("at://did:web:alice.logdate.app/com.atproto.repo.createRecord/entry-1"),
                            cid = "bafyreitest",
                            value = buildJsonObject { put("text", "hello") },
                        ),
                    ),
                cursor = "next",
            )

        val encodedDescribeRepo = json.encodeToString(describeRepo)
        val encodedList = json.encodeToString(ListRecordsResponse.fromPage(page))

        assertTrue(encodedDescribeRepo.contains("\"handle\":\"alice.logdate.app\""))
        assertTrue(encodedList.contains("\"cursor\":\"next\""))
    }

    @Test
    fun `typed repo requests preserve typed identifiers`() {
        val request =
            PutRecordRequest(
                repo = AtprotoDid.require("did:web:alice.logdate.app"),
                collection = Nsid.require("com.atproto.repo.createRecord"),
                recordKey = RecordKey.require("entry-1"),
                record = buildJsonObject { put("text", "hello") },
                swapRecord = "bafy-old",
            )

        assertEquals("did:web:alice.logdate.app", request.repo.toString())
        assertEquals("com.atproto.repo.createRecord", request.collection.toString())
        assertEquals("entry-1", request.recordKey.toString())
        assertEquals("bafy-old", request.swapRecord)
    }
}

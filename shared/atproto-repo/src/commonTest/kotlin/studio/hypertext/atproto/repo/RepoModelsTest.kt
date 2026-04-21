package studio.hypertext.atproto.repo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.AtUri
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for repository-related data models and their serialization.
 *
 * These tests ensure that repository identifiers (e.g., [RepoRecordId]) correctly derive
 * corresponding AT URIs and that core repository types (e.g., [RepoRecord], [RepoWriteResult])
 * serialize into their expected wire formats, including proper handling of validation statuses.
 */
class RepoModelsTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `repo record id derives at uri`() {
        val recordId =
            RepoRecordId(
                repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz"),
                collection = Nsid.require("studio.hypertext.logdate.content"),
                recordKey = RecordKey.require("entry-1"),
            )

        assertEquals(
            AtUri.require("at://did:plc:ewvi7nxzyoun6zhxrhs64oiz/studio.hypertext.logdate.content/entry-1"),
            recordId.uri,
        )
    }

    @Test
    fun `repo record serializes as wire friendly object`() {
        val encoded =
            json.encodeToString(
                RepoRecord.serializer(),
                RepoRecord(
                    uri = AtUri.require("at://did:plc:ewvi7nxzyoun6zhxrhs64oiz/studio.hypertext.logdate.content/entry-1"),
                    cid = "bafkreigh2akiscaildc3w7634dytf7dy4v6zf4a6ri7cl4g6f4ex7zt6ii",
                    value =
                        buildJsonObject {
                            put("type", "TEXT")
                        },
                ),
            )

        assertTrue(encoded.contains("\"uri\":\"at://did:plc:ewvi7nxzyoun6zhxrhs64oiz/studio.hypertext.logdate.content/entry-1\""))
        assertTrue(encoded.contains("\"cid\":\"bafkreigh2akiscaildc3w7634dytf7dy4v6zf4a6ri7cl4g6f4ex7zt6ii\""))
        assertTrue(encoded.contains("\"type\":\"TEXT\""))
    }

    @Test
    fun `repo write result serializes lowercase validation status`() {
        val encoded =
            json.encodeToString(
                RepoWriteResult.serializer(),
                RepoWriteResult(
                    uri = AtUri.require("at://did:plc:ewvi7nxzyoun6zhxrhs64oiz/studio.hypertext.logdate.content/entry-1"),
                    cid = "bafkreigh2akiscaildc3w7634dytf7dy4v6zf4a6ri7cl4g6f4ex7zt6ii",
                    validationStatus = RepoValidationStatus.VALID,
                ),
            )

        assertTrue(encoded.contains("\"validationStatus\":\"valid\""))
    }
}

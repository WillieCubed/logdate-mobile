package app.logdate.client.data.journals

import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class LocalDraftCustodyDeletionTest {
    @Test
    fun `late legacy custody alias stays hidden when mapped destination was deleted`() =
        runTest {
            val f = CustodyFixture()
            f.signIn()
            val source = custodyDraft("Source remains retained behind a deleted alias")
            val mappedId = Uuid.random()
            val fingerprint =
                Json
                    .encodeToString(source)
                    .encodeUtf8()
                    .sha256()
                    .hex()
            val copy =
                DraftCustodyCopy(
                    sourceKey = "editor_drafts_anonymous",
                    sourceId = source.id.toString(),
                    sourceFingerprint = fingerprint,
                    destinationId = mappedId.toString(),
                )
            val sourceJson =
                ArchiveDraftStorageState(
                    drafts = listOf(source),
                    custodyCopies = listOf(copy),
                ).encode(Json)
            f.storage.putStringOrThrow("editor_drafts_anonymous", sourceJson)
            f.storage.putStringOrThrow(
                scopeKey(),
                ArchiveDraftStorageState(
                    localDeletionIds = setOf(mappedId.toString()),
                    custodyCopies = listOf(copy),
                ).encode(Json),
            )
            f.metadata.pending += PendingUpload(source.id.toString(), PendingOperation.UPDATE, scope = f.scope)

            assertTrue(f.repository().getAllDrafts().isEmpty())
            assertEquals(sourceJson, f.storage.getStringOrThrow("editor_drafts_anonymous"))
            assertTrue(
                ArchiveDraftStorageState
                    .decode(Json, f.storage.getStringOrThrow(scopeKey()))
                    .localDeletionIds
                    .contains(mappedId.toString()),
            )
        }

    @Test
    fun `late anonymous source cannot reappear after its scoped draft was deleted`() =
        runTest {
            val f = CustodyFixture()
            f.signIn()
            val draft = custodyDraft("Late source remains private after local deletion")
            f.repository().saveDraft(draft)
            f.repository().deleteDraft(draft.id)
            f.metadata.pending += PendingUpload(draft.id.toString(), PendingOperation.UPDATE, scope = f.scope)
            f.seedAnonymous(draft)

            assertTrue(f.repository().getAllDrafts().isEmpty())
            assertTrue(
                ArchiveDraftStorageState
                    .decode(Json, f.storage.getStringOrThrow(scopeKey()))
                    .localDeletionIds
                    .contains(draft.id.toString()),
            )
            assertTrue(
                f.storage
                    .getStringOrThrow("editor_drafts_anonymous")
                    .orEmpty()
                    .contains("Late source remains private"),
            )
        }

    @Test
    fun `completed transfer and explicit deletion leave no hidden snapshot content`() =
        runTest {
            val f = CustodyFixture()
            val secret = "Synthetic draft removed through explicit deletion"
            val draft = custodyDraft(secret)
            f.seedAnonymous(draft)
            f.metadata.pending += PendingUpload(draft.id.toString(), PendingOperation.UPDATE, scope = f.scope)
            f.signIn()
            f.repository().getAllDraftsForSync()
            f.repository().deleteDraft(draft.id)
            assertTrue(f.repository().getAllDrafts().isEmpty())
            assertFalse(
                f.storage
                    .getStringOrThrow(scopeKey())
                    .orEmpty()
                    .contains(secret),
            )
            assertFalse(
                f.storage
                    .getStringOrThrow("editor_drafts_anonymous")
                    .orEmpty()
                    .contains(secret),
            )
        }

    @Test
    fun `clearing local conflicting aliases removes their hidden source snapshots`() =
        runTest {
            val f = CustodyFixture()
            f.signIn()
            val scoped = custodyDraft("Synthetic scoped content")
            f.repository().saveDraftFromSync(scoped)
            val secret = "Synthetic anonymous conflict removed through clear"
            val source = scoped.copy(blocks = listOf(custodyBlock(secret)))
            f.seedAnonymous(source)
            f.storage.putStringOrThrow("editor_drafts", Json.encodeToString(listOf(source)))
            assertTrue(f.repository().getAllDrafts().size >= 2)
            f.repository().clearAllDrafts()
            assertTrue(f.repository().getAllDrafts().isEmpty())
            for (key in listOf(scopeKey(), "editor_drafts_anonymous", "editor_drafts")) {
                assertFalse(
                    f.storage
                        .getStringOrThrow(key)
                        .orEmpty()
                        .contains(secret),
                )
            }
        }

    private fun scopeKey(): String {
        val origin = "https://server-a.example"
        return "editor_drafts_scope_7:owner-a:${origin.length}:$origin"
    }
}

package app.logdate.client.data.restore

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.data.journals.ArchiveDraftStorageState
import app.logdate.client.data.journals.DraftCustodyCopy
import app.logdate.client.data.journals.DraftMutationReceipt
import app.logdate.client.repository.journals.DraftRecoveryScope
import app.logdate.client.sync.metadata.UploadScope
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RetainedJournalDraftScopeTest {
    private val owner = "347c6231-19f4-49fa-bd1d-58ed34b9be03"
    private val otherOwner = "c4e029bc-0a62-4ec1-9387-cc0f6f1ed5b9"
    private val origin = "https://cloud.example"

    private fun source(bound: Boolean = false) = RetainedJournalSourceScope(owner, bound, origin)

    private fun anonymous() = DraftRecoveryScope(null, null, owner, "https://CLOUD.EXAMPLE:443/")

    @Test
    fun `same unbound installation admits anonymous drafts with exact outbox scope`() {
        assertTrue(source().matchesDraftRecoveryScope(source(), anonymous()))
        assertEquals("editor_drafts_anonymous", source().retainedDraftStorageKey())
    }

    @Test
    fun `anonymous source never enters bound destination through account adoption`() {
        assertFalse(source().matchesDraftRecoveryScope(source(true), DraftRecoveryScope(owner, origin)))
        assertFalse(source().matchesDraftRecoveryScope(source(true).copy(ownerId = otherOwner), DraftRecoveryScope(otherOwner, origin)))
    }

    @Test
    fun `foreign or unknown anonymous source owner stays in custody`() {
        assertFalse(source().matchesDraftRecoveryScope(source().copy(ownerId = otherOwner), anonymous()))
        assertFalse(source().copy(ownerId = null).matchesDraftRecoveryScope(source(), anonymous()))
        assertNull(source().copy(ownerId = null).retainedDraftStorageKey())
    }

    @Test
    fun `unknown or different source origin stays in custody`() {
        assertFalse(source().matchesDraftRecoveryScope(source().copy(backendOrigin = "https://other.example"), anonymous()))
        assertFalse(source().copy(backendOrigin = "").matchesDraftRecoveryScope(source().copy(backendOrigin = ""), anonymous()))
        assertNull(source().copy(backendOrigin = "").retainedDraftStorageKey())
    }

    @Test
    fun `anonymous storage must have both null identity fields`() {
        assertFalse(source().matchesDraftRecoveryScope(source(), anonymous().copy(ownerId = owner)))
        assertFalse(source().matchesDraftRecoveryScope(source(), anonymous().copy(serverOrigin = origin)))
    }

    @Test
    fun `anonymous outbox must retain exact canonical owner and normalized origin`() {
        for (draft in listOf(
            anonymous().copy(outboxOwnerId = null),
            anonymous().copy(outboxOwnerId = otherOwner),
            anonymous().copy(outboxServerOrigin = null),
            anonymous().copy(outboxServerOrigin = "https://other.example"),
        )) {
            assertFalse(source().matchesDraftRecoveryScope(source(), draft))
        }
    }

    @Test
    fun `bound drafts keep their existing owner and raw storage origin key`() {
        val bound = source(true).copy(draftStorageOrigin = "https://CLOUD.EXAMPLE:443")
        assertTrue(bound.matchesDraftRecoveryScope(source(true), DraftRecoveryScope(owner.uppercase(), "https://CLOUD.EXAMPLE:443/")))
        val raw = bound.draftStorageOrigin
        assertEquals("editor_drafts_scope_${owner.length}:$owner:${raw.length}:$raw", bound.retainedDraftStorageKey())
        assertFalse(bound.matchesDraftRecoveryScope(source(true), anonymous()))
    }

    @Test
    fun `only actual draft payload namespaces affect retained coverage`() {
        assertTrue(isRetainedDraftStorageKey("editor_drafts"))
        assertTrue(isRetainedDraftStorageKey("editor_drafts_anonymous"))
        assertTrue(isRetainedDraftStorageKey("editor_drafts_scope_foreign"))
        assertFalse(isRetainedDraftStorageKey("editor_drafts_legacy_owner"))
        assertFalse(isRetainedDraftStorageKey("backend_url"))
    }

    @Test
    fun `bound source does not enter anonymous destination`() {
        assertFalse(source(true).matchesDraftRecoveryScope(source(), anonymous()))
    }

    @Test
    fun `selected metadata only envelopes remain uncovered`() {
        for (state in envelopes()) {
            val prefs = mutablePreferencesOf(stringPreferencesKey("editor_drafts_anonymous") to state.encode(Json))
            val before = prefs.asMap().toMap()
            assertFalse(readRetainedDraftContents(prefs, "editor_drafts_anonymous", Json).covered)
            assertEquals(before, prefs.asMap())
        }
    }

    @Test
    fun `inactive metadata only envelopes remain uncovered`() {
        for (state in envelopes()) {
            val prefs = mutablePreferencesOf(stringPreferencesKey("editor_drafts_scope_foreign") to state.encode(Json))
            val before = prefs.asMap().toMap()
            assertFalse(readRetainedDraftContents(prefs, "editor_drafts_anonymous", Json).covered)
            assertEquals(before, prefs.asMap())
        }
    }

    @Test
    fun `selected same-name non-string preference cannot be considered covered`() {
        val prefs = mutablePreferencesOf(intPreferencesKey("editor_drafts_anonymous") to 1)
        val result = runCatching { readRetainedDraftContents(prefs, "editor_drafts_anonymous", Json) }
        assertTrue(result.exceptionOrNull() != null, "Expected the mismatched key type to fail closed")
    }

    private fun envelopes() =
        listOf(
            ArchiveDraftStorageState(appliedRecoveries = setOf("prior-recovery")),
            ArchiveDraftStorageState(localDeletionIds = setOf(owner)),
            ArchiveDraftStorageState(custodyCopies = listOf(DraftCustodyCopy("source", owner, "fingerprint", otherOwner))),
            ArchiveDraftStorageState(
                pendingMutations = listOf(DraftMutationReceipt("receipt", owner, UploadScope(owner, origin), "UPDATE", emptyList())),
            ),
        )
}

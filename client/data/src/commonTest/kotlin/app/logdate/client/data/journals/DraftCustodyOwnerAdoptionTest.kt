package app.logdate.client.data.journals

import app.logdate.client.data.account.HistoryOwnerAdoption
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.DefaultCanonicalOwnerProvider
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.config.DefaultLogDateConfigRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DraftCustodyOwnerAdoptionTest {
    @Test
    fun `consented first sign in rebinds anonymous draft only with matching old owner outbox proof`() =
        runTest {
            val f = DraftAdoptionFixture()
            val draft = f.prepare()
            assertEquals(listOf(draft), f.repository().getAllDraftsForSync())
            assertTrue(f.metadata.pending.any { it.entityId == draft.id.toString() && it.scope == UploadScope(f.remoteOwner, ORIGIN) })
            assertTrue(ArchiveDraftStorageState.decode(Json, f.storage.getStringOrThrow("editor_drafts_anonymous")).drafts.isEmpty())
        }

    @Test
    fun `consented owner adoption never assigns an anonymous draft to a different server`() =
        runTest {
            val f = DraftAdoptionFixture()
            val draft = f.prepare()
            f.config.updateBackendUrl("https://different.example")
            assertTrue(f.repository().getAllDraftsForSync().isEmpty())
            assertEquals(listOf(draft), f.repository().getAllDrafts())
            f.config.updateBackendUrl(ORIGIN)
            assertEquals(listOf(draft), f.repository().getAllDraftsForSync())
        }

    @Test
    fun `completed adoption without surviving draft custody leaves settled origin unknown`() =
        runTest {
            val f = DraftAdoptionFixture()
            val draft = f.prepare(queued = false)
            assertTrue(f.repository().getAllDraftsForSync().isEmpty())
            assertEquals(listOf(draft), f.repository().getAllDrafts())
            assertTrue(f.repository().hasUnclaimedDraft(draft.id))
        }

    @Test
    fun `a newer remote-owner deletion is never replaced by old-owner adoption proof`() =
        runTest {
            val f = DraftAdoptionFixture()
            val draft = f.prepare()
            f.metadata.pending +=
                PendingUpload(
                    draft.id.toString(),
                    PendingOperation.DELETE,
                    scope = UploadScope(f.remoteOwner, ORIGIN),
                )
            assertTrue(f.repository().getAllDraftsForSync().isEmpty())
            assertEquals(listOf(draft), f.repository().getAllDrafts())
            assertTrue(f.metadata.pending.any { it.entityId == draft.id.toString() && it.operation == PendingOperation.DELETE })
        }

    private companion object {
        const val ORIGIN = "https://first-account.example"
    }
}

private class DraftAdoptionFixture {
    val storage = CustodyStorage()
    val metadata = CustodyMetadata()
    val config = DefaultLogDateConfigRepository(initialBackendUrl = ORIGIN)
    val remoteOwner = Uuid.random().toString()
    private val owner = DefaultCanonicalOwnerProvider(storage)

    suspend fun prepare(queued: Boolean = true): app.logdate.shared.model.EditorDraft {
        val oldOwner = owner.getCanonicalOwnerId()
        val draft = custodyDraft("Created offline before first sign in")
        storage.putStringOrThrow("editor_drafts_anonymous", Json.encodeToString(listOf(draft)))
        if (queued) {
            metadata.pending += PendingUpload(draft.id.toString(), PendingOperation.UPDATE, scope = UploadScope(oldOwner, ORIGIN))
        }
        val adoption = HistoryOwnerAdoption(storage, owner, config, { "synthetic-device" }, { _, _, _, _ -> })
        check(adoption.adopt(remoteOwner))
        return draft
    }

    fun repository() = LocalFirstDraftRepository(storage, Json, AdoptionSignedOut, config, owner, metadata)

    private companion object {
        const val ORIGIN = "https://first-account.example"
    }
}

private object AdoptionSignedOut : SessionStorage {
    override fun getSession(): UserSession? = null

    override fun getSessionFlow() = flowOf<UserSession?>(null)

    override suspend fun hasValidSession() = false

    override suspend fun saveSession(session: UserSession) = error("Signed-out fixture")

    override suspend fun clearSession() = Unit
}

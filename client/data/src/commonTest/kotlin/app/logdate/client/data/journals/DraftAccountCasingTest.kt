package app.logdate.client.data.journals

import app.logdate.client.data.account.DefaultPasskeyAccountRepositoryTest
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.shared.config.DefaultLogDateConfigRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DraftAccountCasingTest {
    @Test
    fun `restored same-account session keeps canonical drafts readable and writable`() =
        runTest {
            val raw = "A8A3400A-9F3C-4FCA-9A7A-7C8CBE5CA24E"
            val owner =
                object : CanonicalOwnerProvider {
                    override suspend fun getCanonicalOwnerId() = raw

                    override suspend fun hasBoundOwner() = true
                }
            val storage = CustodyStorage()
            val sessions = DefaultPasskeyAccountRepositoryTest.FakeSessionStorage()
            val config = DefaultLogDateConfigRepository()
            val original = LocalFirstDraftRepository(storage, Json, sessions, config, owner)
            val draft = custodyDraft("Retained before account access returns")
            original.saveDraft(draft)
            sessions.saveSession(UserSession("synthetic-access", "synthetic-refresh", raw.lowercase()))
            val restored = LocalFirstDraftRepository(storage, Json, sessions, config, owner)
            assertEquals(listOf(draft), restored.getAllDrafts())
            val second = custodyDraft("Written after same-account access returns")
            restored.saveDraft(second)
            assertEquals(setOf(draft, second), restored.getAllDrafts().toSet())
            assertEquals(raw, owner.getCanonicalOwnerId())
            sessions.saveSession(UserSession("other", "other", "b930bf49-d1e9-4a65-b84b-8e74af45012b"))
            assertTrue(restored.getAllDrafts().isEmpty())
            assertFailsWith<IllegalStateException> { restored.saveDraft(custodyDraft("Wrong account")) }
            sessions.clearSession()
            assertEquals(setOf(draft, second), original.getAllDrafts().toSet())
        }
}

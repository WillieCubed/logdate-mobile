package app.logdate.server.database

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountRepository
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.database.support.withH2Database
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PublicIdentityConsentRepositoryTest {
    @Test
    fun `migration leaves existing and new accounts private`() =
        withH2Database(AccountsTable) {
            runBlocking {
                val repository = PostgreSQLAccountRepository()
                val existing = repository.save(account("existing"))
                transaction {
                    exec("ALTER TABLE accounts DROP COLUMN public_identity_did")
                    val sql =
                        requireNotNull(javaClass.classLoader.getResource("db/migration/V54__Add_public_identity_consent.sql"))
                            .readText()
                    exec(sql)
                }
                assertNull(repository.findById(existing.id)?.publicIdentityDid)
                val created = repository.save(account("new").copy(did = "did:web:new.logdate.app"))
                assertNull(repository.findById(created.id)?.publicIdentityDid)
            }
        }

    @Test
    fun `in memory profile saves cannot change consent`() = runBlocking { verifyConsentIsolation(InMemoryAccountRepository()) }

    @Test
    fun `database profile saves cannot change consent and consent survives repository recreation`() =
        withH2Database(AccountsTable) {
            runBlocking {
                val repository = PostgreSQLAccountRepository()
                verifyConsentIsolation(repository)
                val publicAccount =
                    account(
                        "durable",
                    ).copy(did = "did:web:durable.logdate.app", publicIdentityDid = "did:web:durable.logdate.app")
                assertTrue(repository.create(publicAccount))
                assertEquals(publicAccount.did, PostgreSQLAccountRepository().findById(publicAccount.id)?.publicIdentityDid)
            }
        }

    private suspend fun verifyConsentIsolation(repository: AccountRepository) {
        val privateAccount = repository.save(account("private").copy(did = "did:web:private.logdate.app"))
        assertNull(privateAccount.publicIdentityDid)
        assertNull(repository.save(privateAccount.copy(publicIdentityDid = PUBLIC_DID)).publicIdentityDid)
        assertNull(repository.findById(privateAccount.id)?.publicIdentityDid)
        assertFalse(repository.recordPublicIdentity(privateAccount.id, PUBLIC_DID))
        assertTrue(repository.recordPublicIdentity(privateAccount.id, requireNotNull(privateAccount.did)))
        assertEquals(privateAccount.did, repository.findById(privateAccount.id)?.publicIdentityDid)
        assertFalse(repository.recordPublicIdentity(Uuid.random(), requireNotNull(privateAccount.did)))

        val publicAccount = account("public").copy(publicIdentityDid = PUBLIC_DID)
        assertTrue(repository.createOnly(publicAccount))
        assertEquals(PUBLIC_DID, repository.save(publicAccount.copy(displayName = "Renamed", publicIdentityDid = null)).publicIdentityDid)
        assertEquals(PUBLIC_DID, repository.findById(publicAccount.id)?.publicIdentityDid)

        val replacement = requireNotNull(repository.findById(publicAccount.id)).copy(did = "did:web:replacement.logdate.app")
        val updated = requireNotNull(repository.updateIdentityIfUnchanged(publicAccount, replacement))
        assertEquals(replacement.did, updated.did)
        assertEquals(PUBLIC_DID, updated.publicIdentityDid)
        assertEquals(PUBLIC_DID, repository.findById(publicAccount.id)?.publicIdentityDid)
    }

    private fun account(username: String): Account =
        Account(
            id = Uuid.random(),
            username = username,
            displayName = username,
            createdAt = Clock.System.now(),
            did = PUBLIC_DID,
            handle = "$username.logdate.app",
            signingKeyPublic = "zFixtureKey",
        )

    private companion object {
        const val PUBLIC_DID = "did:plc:ewvi7nxzyoun6zhxrhs64oiz"
    }
}

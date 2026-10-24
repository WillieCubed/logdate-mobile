package app.logdate.server.atproto.firehose

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountDeletionService
import app.logdate.server.auth.AccountStatusEvents
import app.logdate.server.identity.IdentityEvents
import app.logdate.server.logdate.CompositeLogDateMediaBlobRepository
import app.logdate.server.logdate.InMemoryLogDateAtprotoBlobRepository
import app.logdate.server.logdate.InMemoryLogDateBackupRepository
import app.logdate.server.logdate.InMemoryLogDateMediaRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.RepoAccountEvent
import studio.hypertext.atproto.repo.RepoIdentityEvent
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.repo.RepoStreamMessage
import studio.hypertext.atproto.repo.RepoSyncEvent
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

/**
 * How accounts reach the stream: when an identity is created or changes, when a publishing run
 * finishes an account, and when an account is deleted. Each path ends in the sequencer, so these tests
 * check the events it stores and the crawl requests the relays receive.
 */
@OptIn(ExperimentalUuidApi::class)
class PublicRepoAnnouncerTest {
    private class Wiring(
        val h: FirehoseHarness,
    ) {
        val crawlRequests = AtomicInteger(0)
        val crawler =
            RelayCrawler(
                httpClient =
                    HttpClient(
                        MockEngine {
                            crawlRequests.incrementAndGet()
                            respond("{}", HttpStatusCode.OK)
                        },
                    ),
                relayHosts = listOf("relay.example.com"),
                serverHostname = "logdate.app",
            )
        val announcer = PublicRepoAnnouncer(h.accounts, h.sequencer, crawler)

        suspend fun events(): List<RepoStreamMessage> = h.eventStore.read(0, 100).map { it.decode() }
    }

    @Test
    fun `the first time an account is announced followers hear its identity and status and relays are asked to crawl`() =
        runBlocking<Unit> {
            val wiring = Wiring(FirehoseHarness())
            val alice = wiring.h.createAccount("alice")

            wiring.announcer.identityChanged(alice)

            val events = wiring.events()
            assertEquals(alice.handle, assertIs<RepoIdentityEvent>(events[0]).handle)
            assertTrue(assertIs<RepoAccountEvent>(events[1]).active)
            assertEquals(2, events.size, "an account with no repository yet has no state to announce")
            assertEquals(1, wiring.crawlRequests.get())
        }

    @Test
    fun `an account that already has a repository also announces its state`() =
        runBlocking<Unit> {
            val wiring = Wiring(FirehoseHarness())
            val alice = wiring.h.createAccount("alice")
            DefaultRepoEngine(wiring.h.rawBlocks)
                .putRecord(
                    RepoRecordId(wiring.h.did(alice), Nsid.require("app.logdate.actor.profile"), RecordKey.require("self")),
                    buildJsonObject { put("\$type", "app.logdate.actor.profile") },
                ).getOrThrow()

            wiring.announcer.accountPublished(alice.id)

            assertIs<RepoSyncEvent>(wiring.events().last())
        }

    @Test
    fun `a later identity change adds an identity event and asks no relay again`() =
        runBlocking<Unit> {
            val wiring = Wiring(FirehoseHarness())
            val alice = wiring.h.createAccount("alice")
            wiring.announcer.identityChanged(alice)
            val before = wiring.events().size

            wiring.announcer.identityChanged(alice.copy(handle = "alice2.logdate.app"))

            val added = wiring.events().drop(before).single()
            assertEquals("alice2.logdate.app", assertIs<RepoIdentityEvent>(added).handle)
            assertEquals(1, wiring.crawlRequests.get())
        }

    @Test
    fun `an account on legacy storage is neither announced nor crawled`() =
        runBlocking<Unit> {
            val wiring = Wiring(FirehoseHarness())
            val legacy = wiring.h.createAccount("legacy", personalSpace = false)

            wiring.announcer.identityChanged(legacy)

            assertTrue(wiring.events().isEmpty())
            assertEquals(0, wiring.crawlRequests.get())
        }

    @Test
    fun `a published account that is unknown to the server is ignored`() =
        runBlocking<Unit> {
            val wiring = Wiring(FirehoseHarness())

            wiring.announcer.accountPublished(kotlin.uuid.Uuid.random())

            assertTrue(wiring.events().isEmpty())
        }

    @Test
    fun `deleting an announced account tells followers it is gone`() =
        runBlocking<Unit> {
            val wiring = Wiring(FirehoseHarness())
            val alice = wiring.h.createAccount("alice")
            wiring.announcer.identityChanged(alice)
            val deletion = deletionService(wiring.h, wiring.announcer)

            deletion.deleteAccount(alice.id)

            val event = assertIs<RepoAccountEvent>(wiring.events().last())
            assertEquals(false, event.active)
            assertEquals("deleted", event.status)
        }

    @Test
    fun `a failing listener never fails the deletion`() =
        runBlocking<Unit> {
            val h = FirehoseHarness()
            val alice = h.createAccount("alice")
            val failing = AccountStatusEvents { _, _, _ -> error("the event log is down") }

            val summary = deletionService(h, failing).deleteAccount(alice.id)

            assertTrue(summary.accountRowDeleted)
        }

    @Test
    fun `a new identity and a new signing key are reported to the listener`() =
        runBlocking<Unit> {
            val h = FirehoseHarness()
            val told = mutableListOf<Account>()
            h.identity.setIdentityEvents(IdentityEvents { told += it })
            val saved =
                h.accounts.save(
                    Account(id = kotlin.uuid.Uuid.random(), username = "carol", displayName = "Carol", createdAt = h.clock.now()),
                )

            val provisioned = h.identity.ensureIdentity(saved)
            assertEquals(listOf(provisioned.did), told.map { it.did })

            h.identity.ensureIdentity(provisioned)
            assertEquals(1, told.size, "an identity that is already complete is not reported again")

            val rotated = h.identity.rotateSigningKey(provisioned)
            assertEquals(2, told.size)
            assertEquals(rotated.account.signingKeyPublic, told.last().signingKeyPublic)
        }

    @Test
    fun `a listener that fails never fails the identity change`() =
        runBlocking<Unit> {
            val h = FirehoseHarness()
            h.identity.setIdentityEvents(IdentityEvents { error("the event log is down") })
            val saved =
                h.accounts.save(
                    Account(id = kotlin.uuid.Uuid.random(), username = "dana", displayName = "Dana", createdAt = h.clock.now()),
                )

            val provisioned = h.identity.ensureIdentity(saved)

            assertTrue(provisioned.did != null)
        }

    private fun deletionService(
        h: FirehoseHarness,
        events: AccountStatusEvents,
    ) = AccountDeletionService(
        accountRepository = h.accounts,
        mediaBlobRepository = CompositeLogDateMediaBlobRepository(InMemoryLogDateMediaRepository(), InMemoryLogDateAtprotoBlobRepository()),
        backupRepository = InMemoryLogDateBackupRepository(),
        blobStorage = h.blobStorage,
        statusEvents = events,
    )
}

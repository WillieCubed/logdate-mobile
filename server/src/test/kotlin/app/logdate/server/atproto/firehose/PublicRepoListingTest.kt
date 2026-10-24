package app.logdate.server.atproto.firehose

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.DAG_CBOR_CODEC
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * `listRepos` is open to anyone, so one call must do a bounded amount of work however many accounts
 * the directory holds and however many of them cannot be shown, and a malformed row must never stop it.
 */
class PublicRepoListingTest {
    private val someCid = Cid.sha256(DAG_CBOR_CODEC, byteArrayOf(1)).toString()

    private class CountingDirectory(
        private val rows: List<PublicRepoCandidate>,
    ) : PublicRepoDirectory {
        var calls = 0
        var returned = 0

        override suspend fun after(
            afterDid: String?,
            limit: Int,
        ): List<PublicRepoCandidate> {
            calls++
            return rows
                .filter { afterDid == null || it.did > afterDid }
                .take(limit)
                .also { returned += it.size }
        }
    }

    private fun candidate(did: String) = PublicRepoCandidate(did, someCid, someCid, revision = 1)

    private fun access(directory: PublicRepoDirectory): PublicRepoAccess =
        PublicRepoAccess(
            gate =
                object : PublicRepoGate {
                    override suspend fun mayShow(did: studio.hypertext.atproto.identity.AtprotoDid) = true

                    override suspend fun standing(did: studio.hypertext.atproto.identity.AtprotoDid): RepoStanding? = RepoStanding.Active
                },
            policy = PublicRepoPolicy(),
            blocks = InMemoryRepoBlockStore(),
            directory = directory,
        )

    @Test
    fun `a page of unparseable DIDs ends instead of looping and moves the cursor past them`() =
        runBlocking<Unit> {
            val rows = (1..30).map { candidate("did:plc:NOT-VALID-$it".padEnd(20, 'x')) }.sortedBy { it.did }
            val directory = CountingDirectory(rows)

            val page = withTimeout(10.seconds) { access(directory).listRepos(cursor = null, limit = 10) }

            assertTrue(page.repos.isEmpty())
            assertTrue(directory.calls <= 3, "at most three pages of candidates are examined, saw ${directory.calls}")
            assertEquals(rows[29].did, page.cursor)
        }

    @Test
    fun `a page whose rows are all unparseable and fewer than the limit ends with no cursor`() =
        runBlocking<Unit> {
            val directory = CountingDirectory(listOf(candidate("not a did"), candidate("also not")))

            val page = withTimeout(10.seconds) { access(directory).listRepos(cursor = null, limit = 10) }

            assertTrue(page.repos.isEmpty())
            assertNull(page.cursor)
            assertEquals(1, directory.calls)
        }

    @Test
    fun `the work for one call is bounded however many candidates cannot be shown`() =
        runBlocking<Unit> {
            val directory = CountingDirectory((1..5000).map { candidate("did:web:a${it.toString().padStart(5, '0')}.example.com") })

            val page = access(directory).listRepos(cursor = null, limit = 20)

            assertTrue(directory.calls <= 3)
            assertTrue(directory.returned <= 60)
            assertTrue(page.cursor != null, "the caller continues from where the search stopped")
        }
}

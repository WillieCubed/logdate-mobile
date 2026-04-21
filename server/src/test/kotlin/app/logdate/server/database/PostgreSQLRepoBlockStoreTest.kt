package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import kotlinx.coroutines.runBlocking
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.RepoBlock
import studio.hypertext.atproto.repo.RepoCommit
import studio.hypertext.atproto.repo.RepoHead
import studio.hypertext.atproto.repo.SignedRepoCommit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [PostgreSQLRepoBlockStore] focused on AT Protocol repository storage.
 *
 * This test suite validates the persistence layer for:
 * - Repository heads (tracking the latest commit and revision).
 * - Content-addressed blocks (IPLD data).
 * - Repository commits and their cryptographic links.
 *
 * It also ensures that repository isolation is maintained during data cleanup operations,
 * confirming that clearing one repository does not affect others.
 */
class PostgreSQLRepoBlockStoreTest {
    @Test
    fun `repo block store persists heads blocks and commits by repo`() {
        withH2Database(
            AtprotoRepoHeadsTable,
            AtprotoRepoBlocksTable,
            AtprotoRepoBlockLinksTable,
            AtprotoRepoCommitsTable,
        ) {
            val store = PostgreSQLRepoBlockStore()
            val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val otherRepo = AtprotoDid.require("did:plc:7wbylgv44bzg5gkq4h6w3lby")
            val rootCid = Cid.sha256(DAG_CBOR_CODEC_VALUE, "root".encodeToByteArray())
            val commitCid = Cid.sha256(DAG_CBOR_CODEC_VALUE, "commit".encodeToByteArray())
            val repoBlock = RepoBlock(cid = rootCid, bytes = "root".encodeToByteArray())
            val otherBlock =
                RepoBlock(
                    cid = Cid.sha256(DAG_CBOR_CODEC_VALUE, "other".encodeToByteArray()),
                    bytes = "other".encodeToByteArray(),
                )
            val commit =
                SignedRepoCommit(
                    cid = commitCid,
                    commit =
                        RepoCommit(
                            repo = repo,
                            root = rootCid,
                            revision = 1L,
                            createdAtEpochMillis = 100L,
                            recordCount = 1,
                        ),
                    signature = "sig-1",
                )
            val head = RepoHead(repo = repo, root = rootCid, commitCid = commitCid, revision = 1L)

            runBlocking {
                store.writeBlock(repo, repoBlock).getOrThrow()
                store.writeBlock(otherRepo, otherBlock).getOrThrow()
                store.appendCommit(repo, commit).getOrThrow()
                store.writeHead(head).getOrThrow()
            }

            runBlocking {
                assertEquals(head, store.readHead(repo).getOrThrow())
                val listedBlocks = store.listBlocks(repo).getOrThrow()
                assertEquals(listOf(commit), store.listCommits(repo).getOrThrow())
                val loadedOtherBlock = store.readBlock(otherBlock.cid).getOrThrow()

                assertEquals(1, listedBlocks.size)
                assertRepoBlockEquals(repoBlock, listedBlocks.single())
                assertNotNull(loadedOtherBlock)
                assertRepoBlockEquals(otherBlock, loadedOtherBlock)
            }
        }
    }

    @Test
    fun `clearing a repo removes only repo scoped rows`() {
        withH2Database(
            AtprotoRepoHeadsTable,
            AtprotoRepoBlocksTable,
            AtprotoRepoBlockLinksTable,
            AtprotoRepoCommitsTable,
        ) {
            val store = PostgreSQLRepoBlockStore()
            val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val otherRepo = AtprotoDid.require("did:plc:7wbylgv44bzg5gkq4h6w3lby")
            val sharedBlockCid = Cid.sha256(DAG_CBOR_CODEC_VALUE, "shared".encodeToByteArray())
            val sharedBlock = RepoBlock(cid = sharedBlockCid, bytes = "shared".encodeToByteArray())
            val repoCommitCid = Cid.sha256(DAG_CBOR_CODEC_VALUE, "repo-commit".encodeToByteArray())
            val otherCommitCid = Cid.sha256(DAG_CBOR_CODEC_VALUE, "other-commit".encodeToByteArray())

            runBlocking {
                store.writeBlock(repo, sharedBlock).getOrThrow()
                store.writeBlock(otherRepo, sharedBlock).getOrThrow()
                store
                    .appendCommit(
                        repo,
                        SignedRepoCommit(
                            cid = repoCommitCid,
                            commit =
                                RepoCommit(
                                    repo = repo,
                                    root = sharedBlockCid,
                                    revision = 1L,
                                    createdAtEpochMillis = 1L,
                                    recordCount = 1,
                                ),
                            signature = "sig-repo",
                        ),
                    ).getOrThrow()
                store
                    .appendCommit(
                        otherRepo,
                        SignedRepoCommit(
                            cid = otherCommitCid,
                            commit =
                                RepoCommit(
                                    repo = otherRepo,
                                    root = sharedBlockCid,
                                    revision = 1L,
                                    createdAtEpochMillis = 2L,
                                    recordCount = 1,
                                ),
                            signature = "sig-other",
                        ),
                    ).getOrThrow()
                store.writeHead(RepoHead(repo, sharedBlockCid, repoCommitCid, 1L)).getOrThrow()
                store.writeHead(RepoHead(otherRepo, sharedBlockCid, otherCommitCid, 1L)).getOrThrow()

                store.clearRepo(repo).getOrThrow()

                assertNull(store.readHead(repo).getOrThrow())
                assertTrue(store.listBlocks(repo).getOrThrow().isEmpty())
                assertTrue(store.listCommits(repo).getOrThrow().isEmpty())
                assertNotNull(store.readBlock(sharedBlockCid).getOrThrow())
                assertEquals(RepoHead(otherRepo, sharedBlockCid, otherCommitCid, 1L), store.readHead(otherRepo).getOrThrow())
                assertEquals(1, store.listBlocks(otherRepo).getOrThrow().size)
                assertEquals(1, store.listCommits(otherRepo).getOrThrow().size)
            }
        }
    }
}

private const val DAG_CBOR_CODEC_VALUE = 0x71

private fun assertRepoBlockEquals(
    expected: RepoBlock,
    actual: RepoBlock,
) {
    assertEquals(expected.cid, actual.cid)
    assertContentEquals(expected.bytes, actual.bytes)
}

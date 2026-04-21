package studio.hypertext.atproto.repo

import studio.hypertext.atproto.identity.AtprotoDid
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for repository block storage implementations.
 *
 * These tests verify the core behaviors of [RepoBlockStore], including block persistence,
 * commit history management, and the ability to clear repository-specific state while
 * maintaining global block availability.
 */
class RepoBlockStoreTest {
    @Test
    fun `clearing a repo removes its head blocks and commit history`() {
        val store = InMemoryRepoBlockStore()
        val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
        val root = Cid.sha256(DAG_CBOR_CODEC, "root".encodeToByteArray())
        val commitCid = Cid.sha256(DAG_CBOR_CODEC, "commit".encodeToByteArray())
        val block = RepoBlock(cid = root, bytes = "root".encodeToByteArray())
        val commit =
            SignedRepoCommit(
                cid = commitCid,
                commit =
                    RepoCommit(
                        repo = repo,
                        root = root,
                        revision = 1L,
                        createdAtEpochMillis = 10L,
                        recordCount = 1,
                    ),
                signature = "sig-1",
            )

        runSuspend { store.writeBlock(repo, block).getOrThrow() }
        runSuspend { store.appendCommit(repo, commit).getOrThrow() }
        runSuspend {
            store
                .writeHead(
                    RepoHead(
                        repo = repo,
                        root = root,
                        commitCid = commitCid,
                        revision = 1L,
                    ),
                ).getOrThrow()
        }

        runSuspend { store.clearRepo(repo).getOrThrow() }

        assertNull(runSuspend { store.readHead(repo).getOrThrow() })
        assertTrue(runSuspend { store.listBlocks(repo).getOrThrow() }.isEmpty())
        assertTrue(runSuspend { store.listCommits(repo).getOrThrow() }.isEmpty())
        assertEquals(block, runSuspend { store.readBlock(root).getOrThrow() })
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>) {
                    outcome = result
                }
            },
        )
        return outcome!!.getOrThrow()
    }
}

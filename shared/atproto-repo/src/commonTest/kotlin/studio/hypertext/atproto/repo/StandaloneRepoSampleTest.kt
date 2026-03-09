package studio.hypertext.atproto.repo

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StandaloneRepoSampleTest {
    @Test
    fun `standalone consumer can create export import and read a repo`() =
        runSuspend {
            val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val collection = Nsid.require("studio.hypertext.logdate.entry")
            val recordKey = RecordKey.require("entry-1")
            val record =
                buildJsonObject {
                    put("\$type", collection.toString())
                    put("text", "hello")
                }

            val sourceEngine = DefaultRepoEngine(InMemoryRepoBlockStore())
            val write =
                sourceEngine
                    .createRecord(
                        repo = repo,
                        collection = collection,
                        value = record,
                        recordKey = recordKey,
                    ).getOrThrow()
            val exported = sourceEngine.export(repo).getOrThrow()
            val importedEngine = DefaultRepoEngine(InMemoryRepoBlockStore())

            importedEngine.import(exported).getOrThrow()

            val restored =
                importedEngine
                    .getRecord(
                        RepoRecordId(
                            repo = repo,
                            collection = collection,
                            recordKey = recordKey,
                        ),
                    ).getOrThrow()
            val head = importedEngine.loadHead(repo).getOrThrow()
            val history = importedEngine.listCommits(repo).getOrThrow()

            assertEquals(
                "hello",
                restored
                    ?.value
                    ?.get("text")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(write.cid, restored?.cid)
            assertNotNull(head)
            assertEquals(repo, head.repo)
            assertEquals(1, history.size)
            assertEquals(exported.head, head)
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

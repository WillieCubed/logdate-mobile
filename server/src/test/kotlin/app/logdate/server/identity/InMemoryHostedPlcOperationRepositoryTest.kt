package app.logdate.server.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests for [InMemoryHostedPlcOperationRepository], providing a lightweight
 * implementation for tracking AT Protocol PLC operations during testing or
 * for small deployments.
 *
 * This suite validates the basic persistence and retrieval operations for
 * signed PLC operations, specifically ensuring that operations can be accurately
 * listed and filtered by both the internal account ID and the public AT Protocol DID.
 */
@OptIn(ExperimentalUuidApi::class)
class InMemoryHostedPlcOperationRepositoryTest {
    @Test
    fun `repository stores plc operations by account and did`() =
        kotlinx.coroutines.test.runTest {
            val repository = InMemoryHostedPlcOperationRepository()
            val accountId = Uuid.random()
            val did = "did:plc:ewvi7nxzyoun6zhxrhs64oiz"
            val first =
                StoredHostedPlcOperation(
                    id = Uuid.random(),
                    accountId = accountId,
                    did = did,
                    cid = "cid-1",
                    operationType = "plc_operation",
                    operationJson = """{"sig":"one"}""",
                )
            val second =
                StoredHostedPlcOperation(
                    id = Uuid.random(),
                    accountId = accountId,
                    did = did,
                    cid = "cid-2",
                    prevCid = "cid-1",
                    operationType = "plc_operation",
                    operationJson = """{"sig":"two"}""",
                )

            repository.save(first)
            repository.save(second)

            assertEquals(listOf(first, second), repository.listByAccountId(accountId))
            assertEquals(listOf(first, second), repository.listByDid(did))
        }

    @Test
    fun `recording the directory cid fills in an operation saved before it was published`() =
        kotlinx.coroutines.test.runTest {
            val repository = InMemoryHostedPlcOperationRepository()
            val unpublished =
                StoredHostedPlcOperation(
                    id = Uuid.random(),
                    accountId = Uuid.random(),
                    did = "did:plc:ewvi7nxzyoun6zhxrhs64oiz",
                    operationType = "plc_operation",
                    operationJson = """{"sig":"one"}""",
                )
            repository.save(unpublished)

            assertTrue(repository.recordPublishedCid(unpublished.id, "bafy-genesis"))
            assertFalse(repository.recordPublishedCid(Uuid.random(), "bafy-other"))

            assertEquals("bafy-genesis", repository.listByDid(unpublished.did).single().cid)
        }
}

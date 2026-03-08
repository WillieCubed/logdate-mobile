package app.logdate.server.identity

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.plc.PlcDirectoryClient
import studio.hypertext.atproto.plc.PlcLogEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PlcIdentityServiceTest {
    @Test
    fun `provisionHostedDid derives did plc identity and preserves atproto plc document fields`() =
        kotlinx.coroutines.test.runTest {
            val signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek")
            val service =
                PlcIdentityService(
                    signingKeyService = signingKeyService,
                    config =
                        AtprotoIdentityConfig(
                            handleDomain = "logdate.app",
                            pdsServiceEndpoint = "https://pds.logdate.app",
                            hostedAccountDidMethod = HostedAccountDidMethod.PLC,
                        ),
                )
            val accountId = Uuid.random()

            val provisioned = service.provisionHostedDid(accountId = accountId, handle = "alice.logdate.app")
            val repeated = service.provisionHostedDid(accountId = accountId, handle = "alice.logdate.app")

            assertTrue(provisioned.did.startsWith("did:plc:"))
            assertEquals(provisioned.did, repeated.did)
            assertEquals(listOf("at://alice.logdate.app"), provisioned.operation.alsoKnownAs)
            assertEquals(
                "https://pds.logdate.app",
                provisioned.operation.services
                    .getValue("atproto_pds")
                    .endpoint,
            )
            assertEquals(
                provisioned.publicKeyDidKey,
                provisioned.operation.verificationMethods.getValue("atproto"),
            )
            assertEquals(listOf(provisioned.publicKeyDidKey), provisioned.operation.rotationKeys)
            assertEquals(AtprotoDid.require(provisioned.did), service.documentFor(provisioned).id)
        }

    @Test
    fun `provisionHostedDid publishes signed plc operation when publishing is enabled`() =
        kotlinx.coroutines.test.runTest {
            val published = mutableListOf<Pair<String, PlcLogEntry>>()
            val service =
                PlcIdentityService(
                    signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
                    config =
                        AtprotoIdentityConfig(
                            handleDomain = "logdate.app",
                            pdsServiceEndpoint = "https://logdate.app",
                            hostedAccountDidMethod = HostedAccountDidMethod.PLC,
                            publishHostedPlcOperations = true,
                        ),
                    plcDirectoryClient =
                        object : PlcDirectoryClient {
                            override suspend fun getDocument(did: AtprotoDid) = error("unused")

                            override suspend fun getOperationLog(did: AtprotoDid) = error("unused")

                            override suspend fun getAuditLog(did: AtprotoDid) = error("unused")

                            override suspend fun export(
                                after: String?,
                                count: Int?,
                            ) = error("unused")

                            override suspend fun submit(
                                did: AtprotoDid,
                                entry: PlcLogEntry,
                            ): Result<Unit> =
                                runCatching {
                                    published += did.toString() to entry
                                }
                        },
                )

            val provisioned = service.provisionHostedDid(accountId = Uuid.random(), handle = "brie.logdate.app")

            assertEquals(1, published.size)
            assertEquals(provisioned.did, published.single().first)
            assertTrue(published.single().second.isSigned)
        }

    @Test
    fun `identity service provisions did plc accounts by default and preserves existing did web identities`() =
        kotlinx.coroutines.test.runTest {
            val accountRepository = InMemoryAccountRepository()
            val plcIdentityService =
                PlcIdentityService(SigningKeyService(InMemorySigningKeyRepository(), "test-kek"), AtprotoIdentityConfig())
            val service =
                AtprotoIdentityService(
                    accountRepository = accountRepository,
                    signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "test-kek"),
                    config = AtprotoIdentityConfig(),
                    plcIdentityService = plcIdentityService,
                )
            val hosted =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "cora",
                        displayName = "Cora",
                        createdAt = Clock.System.now(),
                    ),
                )
            val existingWeb =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "dana",
                        displayName = "Dana",
                        createdAt = Clock.System.now(),
                        handle = "dana.logdate.app",
                        did = "did:web:dana.logdate.app",
                        signingKeyPublic = "zDanaKey",
                    ),
                )

            val hostedEnsured = service.ensureIdentity(hosted)
            val existingEnsured = service.ensureIdentity(existingWeb)

            assertTrue(hostedEnsured.did?.startsWith("did:plc:") == true)
            assertEquals("cora.logdate.app", hostedEnsured.handle)
            assertEquals("did:web:dana.logdate.app", existingEnsured.did)
            assertFalse(existingEnsured.did?.startsWith("did:plc:") == true)
        }
}

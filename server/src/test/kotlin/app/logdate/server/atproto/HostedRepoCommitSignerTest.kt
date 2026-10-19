package app.logdate.server.atproto

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import app.logdate.server.logdate.syntheticRepoDid
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import studio.hypertext.atproto.crypto.EcKeySupport
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.DAG_CBOR_CODEC
import studio.hypertext.atproto.repo.DagCborCodec
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import studio.hypertext.atproto.repo.RepoCommit
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.repo.RepoSigningKeyUnavailableException
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests for [HostedRepoCommitSigner], ensuring that it correctly signs AT Protocol
 * repository commits using the server's signing infrastructure.
 *
 * This suite verifies that signed repository exports are valid and can be
 * successfully imported and verified by other standard repository engines.
 */
@OptIn(ExperimentalUuidApi::class)
class HostedRepoCommitSignerTest {
    @Test
    fun `hosted repo signer signs exports that another engine can verify`() =
        runTest {
            val accountRepository = InMemoryAccountRepository()
            val signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "hosted-repo-signer-test-kek")
            val identityService =
                AtprotoIdentityService(
                    accountRepository = accountRepository,
                    signingKeyService = signingKeyService,
                    config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
                )
            val account =
                accountRepository.save(
                    Account(
                        id = Uuid.random(),
                        username = "alice",
                        displayName = "Alice",
                        createdAt = Clock.System.now(),
                    ),
                )
            val hostedAccount = identityService.ensureIdentity(account)
            val repoDid = hostedAccount.did!!
            val collection = Nsid.require("studio.hypertext.logdate.entry")
            val recordKey = RecordKey.require("entry-1")
            val signer = HostedRepoCommitSigner(accountRepository, signingKeyService)
            val sourceEngine = DefaultRepoEngine(InMemoryRepoBlockStore(), signer = signer)
            val recordId =
                RepoRecordId(
                    repo =
                        studio.hypertext.atproto.identity.AtprotoDid
                            .require(repoDid),
                    collection = collection,
                    recordKey = recordKey,
                )

            sourceEngine
                .putRecord(
                    recordId = recordId,
                    value =
                        buildJsonObject {
                            put("\$type", collection.toString())
                            put("text", "hello")
                        },
                ).getOrThrow()
            val export = sourceEngine.export(recordId.repo).getOrThrow()

            val importedEngine = DefaultRepoEngine(InMemoryRepoBlockStore(), signer = signer)
            val importedHead = importedEngine.import(export).getOrThrow()
            val importedRecord = importedEngine.getRecord(recordId).getOrThrow()
            val tamperedImport =
                importedEngine
                    .import(
                        export.copy(
                            commits = export.commits.map { commit -> commit.copy(signature = "invalid-signature") },
                        ),
                    ).exceptionOrNull()

            assertEquals(export.head, importedHead)
            assertEquals(
                "hello",
                importedRecord
                    ?.value
                    ?.get("text")
                    ?.toString()
                    ?.trim('"'),
            )
            assertTrue(tamperedImport is IllegalArgumentException)
        }

    @Test
    fun `a write to a repository without an account fails instead of storing an unsigned commit`() =
        runTest {
            val accountRepository = InMemoryAccountRepository()
            val signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "hosted-repo-signer-test-kek")
            val blockStore = InMemoryRepoBlockStore()
            val engine = DefaultRepoEngine(blockStore, signer = HostedRepoCommitSigner(accountRepository, signingKeyService))
            val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")

            val failure = engine.putRecord(entryRecord(repo), entryValue()).exceptionOrNull()

            val unavailable = assertIs<RepoSigningKeyUnavailableException>(failure)
            assertEquals(repo, unavailable.repo)
            assertNull(blockStore.readHead(repo).getOrThrow())
            assertTrue(blockStore.listCommits(repo).getOrThrow().isEmpty())
        }

    @Test
    fun `a write fails when the account has no active signing key`() =
        runTest {
            val accountRepository = InMemoryAccountRepository()
            val keyRepository = InMemorySigningKeyRepository()
            val signingKeyService = SigningKeyService(keyRepository, "hosted-repo-signer-test-kek")
            val account = hostedAccount(accountRepository, signingKeyService)
            keyRepository.revokeActiveKeys(account.id)
            val repo = AtprotoDid.require(account.did!!)
            val blockStore = InMemoryRepoBlockStore()
            val engine = DefaultRepoEngine(blockStore, signer = HostedRepoCommitSigner(accountRepository, signingKeyService))

            val failure = engine.putRecord(entryRecord(repo), entryValue()).exceptionOrNull()

            assertIs<RepoSigningKeyUnavailableException>(failure)
            assertNull(blockStore.readHead(repo).getOrThrow())
            assertNull(
                keyRepository.findActiveByAccountId(account.id),
                "Signing must not quietly create a key the DID document does not list",
            )
        }

    @Test
    fun `verifying a commit for a repository without an account fails clearly`() =
        runTest {
            val signer =
                HostedRepoCommitSigner(
                    InMemoryAccountRepository(),
                    SigningKeyService(InMemorySigningKeyRepository(), "hosted-repo-signer-test-kek"),
                )
            val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
            val commit =
                RepoCommit(
                    repo = repo,
                    root = Cid.sha256(DAG_CBOR_CODEC, "root".encodeToByteArray()),
                    revision = 1,
                    createdAtEpochMillis = 0,
                    recordCount = 0,
                )

            assertFailsWith<RepoSigningKeyUnavailableException> { signer.verify(commit, byteArrayOf(1), "c2ln") }
        }

    @Test
    fun `hosted commits carry a signature that checks out against the published key`() =
        runTest {
            val accountRepository = InMemoryAccountRepository()
            val signingKeyService = SigningKeyService(InMemorySigningKeyRepository(), "hosted-repo-signer-test-kek")
            val account = hostedAccount(accountRepository, signingKeyService)
            val repo = AtprotoDid.require(account.did!!)
            val blockStore = InMemoryRepoBlockStore()
            DefaultRepoEngine(blockStore, signer = HostedRepoCommitSigner(accountRepository, signingKeyService))
                .putRecord(entryRecord(repo), entryValue())
                .getOrThrow()

            val head = assertNotNull(blockStore.readHead(repo).getOrThrow())
            val signed = DagCborCodec.decode(assertNotNull(blockStore.readBlock(head.commitCid).getOrThrow()).bytes).jsonObject
            val unsigned = DagCborCodec.encode(JsonObject(signed - "sig"))
            val signature =
                Base64.getDecoder().decode(
                    signed
                        .getValue("sig")
                        .jsonObject
                        .getValue("\$bytes")
                        .jsonPrimitive.content,
                )
            val publishedKey = EcKeySupport.decodePublicKey(assertNotNull(accountRepository.findByDid(repo.toString())?.signingKeyPublic))

            assertTrue(EcKeySupport.verifySha256(publishedKey.publicKey, publishedKey.curve, unsigned, signature))
        }

    @Test
    fun `sync storage for an account without a DID is sealed with a digest instead of failing`() =
        runTest {
            val signer =
                HostedRepoCommitSigner(
                    InMemoryAccountRepository(),
                    SigningKeyService(InMemorySigningKeyRepository(), "hosted-repo-signer-test-kek"),
                )
            val repo = AtprotoDid.require(syntheticRepoDid(UUID.randomUUID()))
            val engine = DefaultRepoEngine(InMemoryRepoBlockStore(), signer = signer)

            engine.putRecord(entryRecord(repo), entryValue()).getOrThrow()

            val export = engine.export(repo).getOrThrow()
            assertEquals(export.head, DefaultRepoEngine(InMemoryRepoBlockStore(), signer = signer).import(export).getOrThrow())
        }

    private suspend fun hostedAccount(
        accountRepository: InMemoryAccountRepository,
        signingKeyService: SigningKeyService,
    ): Account {
        val identityService =
            AtprotoIdentityService(
                accountRepository = accountRepository,
                signingKeyService = signingKeyService,
                config = AtprotoIdentityConfig(handleDomain = "logdate.app", pdsServiceEndpoint = "https://logdate.app"),
            )
        val account =
            accountRepository.save(
                Account(
                    id = Uuid.random(),
                    username = "alice",
                    displayName = "Alice",
                    createdAt = Clock.System.now(),
                ),
            )
        return identityService.ensureIdentity(account)
    }

    private fun entryRecord(repo: AtprotoDid) =
        RepoRecordId(
            repo = repo,
            collection = Nsid.require("studio.hypertext.logdate.entry"),
            recordKey = RecordKey.require("entry-1"),
        )

    private fun entryValue() =
        buildJsonObject {
            put("\$type", "studio.hypertext.logdate.entry")
            put("text", "hello")
        }
}

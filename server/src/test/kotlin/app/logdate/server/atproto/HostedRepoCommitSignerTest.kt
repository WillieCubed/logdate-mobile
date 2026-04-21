package app.logdate.server.atproto

import app.logdate.server.auth.Account
import app.logdate.server.auth.InMemoryAccountRepository
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.identity.AtprotoIdentityService
import app.logdate.server.identity.InMemorySigningKeyRepository
import app.logdate.server.identity.SigningKeyService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import kotlin.test.Test
import kotlin.test.assertEquals
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
}

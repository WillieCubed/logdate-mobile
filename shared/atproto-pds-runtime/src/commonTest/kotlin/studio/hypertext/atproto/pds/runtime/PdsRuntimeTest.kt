package studio.hypertext.atproto.pds.runtime

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.pds.AuthorizationServerMetadata
import studio.hypertext.atproto.pds.BlobDownload
import studio.hypertext.atproto.pds.BlobRef
import studio.hypertext.atproto.pds.CidLink
import studio.hypertext.atproto.pds.CreateRecordRequest
import studio.hypertext.atproto.pds.DeleteRecordRequest
import studio.hypertext.atproto.pds.DescribeServerResponse
import studio.hypertext.atproto.pds.GetBlobRequest
import studio.hypertext.atproto.pds.GetLatestCommitRequest
import studio.hypertext.atproto.pds.GetRecordRequest
import studio.hypertext.atproto.pds.GetRepoRequest
import studio.hypertext.atproto.pds.GetRepoStatusRequest
import studio.hypertext.atproto.pds.ListRecordsRequest
import studio.hypertext.atproto.pds.ProtectedResourceMetadata
import studio.hypertext.atproto.pds.PutRecordRequest
import studio.hypertext.atproto.pds.UploadBlobRequest
import studio.hypertext.atproto.repo.CarCodec
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.DefaultRepoEngine
import studio.hypertext.atproto.repo.InMemoryRepoBlockStore
import studio.hypertext.atproto.repo.RepoRecordId
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import studio.hypertext.atproto.syntax.Tid
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the core Personal Data Server (PDS) runtime services.
 *
 * This test suite exercises the primary components responsible for PDS operation:
 * - [StaticPdsDiscoveryService]: Delivery of OAuth and server discovery metadata.
 * - [DefaultPdsRepoService]: Management of repository data through high-level XRPC-like interfaces.
 * - [DefaultPdsBlobService]: Handling of binary content (blobs) with persistence.
 * - [DefaultPdsSyncService]: Facilitating repository synchronization via CAR file exports
 *   and commit status reporting.
 */
class PdsRuntimeTest {
    private val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
    private val collection = Nsid.require("studio.hypertext.logdate.content")
    private val recordKey = RecordKey.require("entry-1")

    @Test
    fun `static discovery service returns configured metadata`() {
        val authorization =
            AuthorizationServerMetadata(
                issuer = "https://logdate.app",
                authorization_endpoint = "https://logdate.app/oauth/authorize",
                token_endpoint = "https://logdate.app/oauth/token",
                pushed_authorization_request_endpoint = "https://logdate.app/oauth/par",
                revocation_endpoint = "https://logdate.app/oauth/revoke",
                jwks_uri = "https://logdate.app/oauth/jwks",
                response_types_supported = listOf("code"),
                grant_types_supported = listOf("authorization_code"),
                code_challenge_methods_supported = listOf("S256"),
                token_endpoint_auth_methods_supported = listOf("none", "private_key_jwt"),
                token_endpoint_auth_signing_alg_values_supported = listOf("ES256"),
                dpop_signing_alg_values_supported = listOf("ES256"),
                scopes_supported = listOf("atproto"),
                authorization_response_iss_parameter_supported = true,
                require_pushed_authorization_requests = true,
                client_id_metadata_document_supported = true,
            )
        val protected = ProtectedResourceMetadata(resource = "https://logdate.app", authorization_servers = listOf("https://logdate.app"))
        val describe = DescribeServerResponse("did:web:logdate.app", listOf("logdate.app"), false, false)

        val service = StaticPdsDiscoveryService(authorization, protected, describe)

        assertEquals(authorization, service.authorizationServerMetadata())
        assertEquals(protected, service.protectedResourceMetadata())
        assertEquals(describe, service.describeServer())
    }

    @Test
    fun `default repo service proxies repo reads writes and cid constraints`() =
        kotlinx.coroutines.test.runTest {
            val repoStore = DefaultRepoEngine(InMemoryRepoBlockStore())
            val service = DefaultPdsRepoService(repoStore)
            val record =
                buildJsonObject {
                    put("\$type", collection.toString())
                    put("content", "hello")
                }

            val created =
                service
                    .createRecord(
                        CreateRecordRequest(
                            repo = repo,
                            collection = collection,
                            record = record,
                            recordKey = recordKey,
                        ),
                    ).getOrThrow()
            val fetched =
                service
                    .getRecord(
                        GetRecordRequest(
                            repo = repo,
                            collection = collection,
                            recordKey = recordKey,
                            cid = created.cid,
                        ),
                    ).getOrThrow()
            val cidMismatch =
                service
                    .getRecord(
                        GetRecordRequest(
                            repo = repo,
                            collection = collection,
                            recordKey = recordKey,
                            cid = "bafy-other",
                        ),
                    ).getOrThrow()
            val listed = service.listRecords(ListRecordsRequest(repo = repo, collection = collection)).getOrThrow()
            val updated =
                service
                    .putRecord(
                        PutRecordRequest(
                            repo = repo,
                            collection = collection,
                            recordKey = recordKey,
                            record =
                                buildJsonObject {
                                    put("\$type", collection.toString())
                                    put("content", "updated")
                                },
                            swapRecord = created.cid,
                        ),
                    ).getOrThrow()
            val deleted =
                service
                    .deleteRecord(
                        DeleteRecordRequest(
                            repo = repo,
                            collection = collection,
                            recordKey = recordKey,
                            swapRecord = updated.cid,
                        ),
                    ).getOrThrow()
            val afterDelete =
                repoStore
                    .getRecord(
                        RepoRecordId(
                            repo = repo,
                            collection = collection,
                            recordKey = recordKey,
                        ),
                    ).getOrThrow()

            assertNotNull(fetched)
            assertNull(cidMismatch)
            assertEquals(listOf(created.cid), listed.records.mapNotNull { it.cid })
            assertTrue(deleted)
            assertNull(afterDelete)
        }

    @Test
    fun `default blob service proxies blob uploads and downloads`() =
        kotlinx.coroutines.test.runTest {
            val cid = Cid.require("bafkreihdwdcefgh4dqkjv67uzcmw7ojee6xedzdetojuzjevtenxquvyku")
            val blobRef =
                BlobRef(
                    ref = CidLink(cid),
                    mimeType = "image/jpeg",
                    size = 3L,
                )
            val blobStore =
                object : PdsBlobStore {
                    override suspend fun putBlob(request: UploadBlobRequest): Result<BlobRef> {
                        assertEquals(repo, request.repo)
                        assertEquals("image/jpeg", request.contentType)
                        assertContentEquals(byteArrayOf(1, 2, 3), request.bytes)
                        return Result.success(blobRef)
                    }

                    override suspend fun getBlob(request: GetBlobRequest): Result<BlobDownload?> {
                        assertEquals(repo, request.did)
                        assertEquals(cid, request.cid)
                        return Result.success(BlobDownload(contentType = "image/jpeg", bytes = byteArrayOf(1, 2, 3)))
                    }
                }
            val service = DefaultPdsBlobService(blobStore)

            val uploaded =
                service
                    .uploadBlob(
                        UploadBlobRequest(
                            repo = repo,
                            contentType = "image/jpeg",
                            bytes = byteArrayOf(1, 2, 3),
                        ),
                    ).getOrThrow()
            val downloaded =
                service
                    .getBlob(
                        GetBlobRequest(
                            did = repo,
                            cid = cid,
                        ),
                    ).getOrThrow()

            assertEquals(blobRef, uploaded.blob)
            assertEquals("image/jpeg", downloaded?.contentType)
            assertContentEquals(byteArrayOf(1, 2, 3), downloaded?.bytes)
        }

    @Test
    fun `default sync service exports repo bytes and reports latest commit state`() =
        kotlinx.coroutines.test.runTest {
            val repoStore = DefaultRepoEngine(InMemoryRepoBlockStore())
            val syncService = DefaultPdsSyncService(repoStore)
            val record =
                buildJsonObject {
                    put("\$type", collection.toString())
                    put("content", "hello")
                }

            val firstWrite =
                repoStore
                    .createRecord(
                        repo = repo,
                        collection = collection,
                        value = record,
                        recordKey = recordKey,
                    ).getOrThrow()
            val firstHead = requireNotNull(repoStore.loadHead(repo).getOrThrow())
            repoStore
                .putRecord(
                    recordId =
                        RepoRecordId(
                            repo = repo,
                            collection = collection,
                            recordKey = RecordKey.require("entry-2"),
                        ),
                    value =
                        buildJsonObject {
                            put("\$type", collection.toString())
                            put("content", "second")
                        },
                ).getOrThrow()

            val export = syncService.getRepo(GetRepoRequest(did = repo, since = Tid.fromLong(firstHead.revision))).getOrThrow()
            val latestCommit = syncService.getLatestCommit(GetLatestCommitRequest(did = repo)).getOrThrow()
            val repoStatus = syncService.getRepoStatus(GetRepoStatusRequest(did = repo)).getOrThrow()
            val exportRepo = CarCodec.read(requireNotNull(export).bytes)

            assertEquals(DefaultPdsSyncService.CAR_CONTENT_TYPE, export.contentType)
            assertEquals(repo, exportRepo.repo)
            assertEquals(listOf(2L), exportRepo.commits.map { it.commit.revision })
            assertTrue(exportRepo.blocks.none { block -> block.cid.toString() == firstWrite.cid })
            assertNotNull(latestCommit)
            assertEquals(2L, latestCommit.rev.toLong())
            assertNotNull(repoStatus)
            assertTrue(repoStatus.active)
            assertEquals(repo, repoStatus.did)
            assertEquals(2L, repoStatus.rev?.toLong())
        }
}

package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.CreateRecordInput
import app.logdate.server.routes.DeleteRecordInput
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.ErrorEnvelope
import app.logdate.server.routes.PutRecordInput
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.bearerOrDpopOperation
import app.logdate.server.routes.bearerUnauthorized
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.ok
import app.logdate.server.routes.pdsError
import app.logdate.server.routes.publicOperation
import io.github.smiley4.ktoropenapi.config.RequestConfig
import io.github.smiley4.ktoropenapi.config.ResponsesConfig
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.pds.BlobRef
import studio.hypertext.atproto.pds.CidLink
import studio.hypertext.atproto.pds.CreateAccountRequest
import studio.hypertext.atproto.pds.CreateSessionRequest
import studio.hypertext.atproto.pds.DescribeRepoResponse
import studio.hypertext.atproto.pds.DescribeServerResponse
import studio.hypertext.atproto.pds.EmptyPdsResponse
import studio.hypertext.atproto.pds.GetLatestCommitResponse
import studio.hypertext.atproto.pds.GetRepoStatusResponse
import studio.hypertext.atproto.pds.ListRecordsResponse
import studio.hypertext.atproto.pds.ResolveHandleResponse
import studio.hypertext.atproto.pds.SessionInfoResponse
import studio.hypertext.atproto.pds.SessionResponse
import studio.hypertext.atproto.pds.UploadBlobResponse
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.RepoRecord
import studio.hypertext.atproto.repo.RepoValidationStatus
import studio.hypertext.atproto.repo.RepoWriteResult
import studio.hypertext.atproto.syntax.AtUri
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import studio.hypertext.atproto.syntax.Tid

/**
 * Documentation for the AT Protocol XRPC methods in `XrpcRoutes.kt`. Method names are lexicon IDs
 * (`com.atproto.repo.getRecord`); descriptions explain them for readers who have never used AT Protocol.
 */
internal object XrpcDocs {
    private const val COLLECTION = "studio.hypertext.logdate.content"
    private const val RKEY = "3lcfmzdwnz22c"
    private const val CID = "bafyreiha2g4xx3ny4ftcxg5dydjklm6q2nw7rz4bqdxu6yi3k7ovz5m7ei"
    private const val COMMIT_CID = "bafyreib2rxk3rh6kzwq5m5nq6tze3wgqqtlsm2pjpaom6xkf5x2yopcd3y"
    private const val REV = "3lcfmzdwnz22c"
    private const val ACCESS_JWT = "eyJhbGciOiJIUzI1NiJ9.atproto-access-example"
    private const val REFRESH_JWT = "eyJhbGciOiJIUzI1NiJ9.atproto-refresh-example"

    private val did = AtprotoDid.require(DocExamples.DID)
    private val recordUri = AtUri.require("at://${DocExamples.DID}/$COLLECTION/$RKEY")
    private val record =
        buildJsonObject {
            put("\$type", JsonPrimitive(COLLECTION))
            put("text", JsonPrimitive(SyncExamples.NOTE_TEXT))
            put("createdAt", JsonPrimitive(DocExamples.ISO_NOW))
        }

    private val unsupported =
        ErrorCase(
            "Unsupported",
            "This deployment has the AT Protocol service behind this method switched off. Check `capabilities` on **Describe this server**.",
            "Repo service is not configured",
        )
    private val invalidRequest =
        ErrorCase(
            "InvalidRequest",
            "A required parameter is missing or malformed. The `message` names it.",
            "repo, collection, and rkey are required",
        )
    private val repoMismatch =
        ErrorCase(
            "RepoMismatch",
            "The `repo` in the body is not the authenticated account's DID or handle. You can only write to your own repository.",
            "Authenticated account does not own repo",
        )
    private val invalidSwap =
        ErrorCase(
            "InvalidSwap",
            "`swapRecord` or `swapCommit` did not match the current state: someone wrote in between. Re-read and try again.",
            "swapRecord did not match",
        )
    private val repoNotFound = ErrorCase("RepoNotFound", "No repository with that DID is hosted here.", "Unknown repo: did:plc:…")

    private fun ResponsesConfig.notConfigured() = pdsError(HttpStatusCode.NotImplemented, unsupported)

    private fun RequestConfig.didParameter() {
        queryParameter<String>("did") {
            description = "The repository's DID."
            required = true
            example("Example") { value = DocExamples.DID }
        }
    }

    private fun RequestConfig.repoParameter() {
        queryParameter<String>("repo") {
            description = "The repository, as a DID or a handle."
            required = true
            example("Example") { value = DocExamples.HANDLE }
        }
    }

    private val session =
        SessionResponse(
            accessJwt = ACCESS_JWT,
            refreshJwt = REFRESH_JWT,
            handle = DocExamples.HANDLE,
            did = did,
            email = DocExamples.EMAIL,
            emailConfirmed = true,
            active = true,
        )

    // ---- Identity and server ------------------------------------------------------------------

    val resolveHandle: RouteConfig.() -> Unit = {
        publicOperation(
            "resolveHandle",
            ApiTags.XRPC,
            "Resolve a handle",
            """
            `com.atproto.identity.resolveHandle`. Turns a handle such as `willie.logdate.app` into the DID it
            currently points at. A handle is the human-readable name; the DID is the stable identifier
            everything else keys on, so most other methods want the DID.
            """,
        )
        request {
            queryParameter<String>("handle") {
                description = "The handle to resolve, without `@`."
                required = true
                example("Example") { value = DocExamples.HANDLE }
            }
        }
        response {
            ok("The handle's DID.", ResolveHandleResponse(did = did))
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase("InvalidRequest", "`handle` is missing.", "Handle is required"),
                ErrorCase("HandleNotFound", "No account here has that handle.", "Handle not found: willie.logdate.app"),
            )
        }
    }

    val describeServer: RouteConfig.() -> Unit = {
        publicOperation(
            "describeAtprotoServer",
            ApiTags.XRPC,
            "Describe the PDS",
            """
            `com.atproto.server.describeServer`. Returns the server's own DID, the handle domains it can issue,
            and whether account creation needs an invite code or a phone number (it needs neither here). The
            AT Protocol equivalent of **Describe this server**.
            """,
        )
        response {
            ok(
                "The PDS description.",
                DescribeServerResponse(
                    did = "did:web:cloud.logdate.app",
                    availableUserDomains = listOf("logdate.app"),
                    inviteCodeRequired = false,
                    phoneVerificationRequired = false,
                ),
            )
        }
    }

    val describeRepo: RouteConfig.() -> Unit = {
        publicOperation(
            "describeRepo",
            ApiTags.XRPC,
            "Describe a repository",
            """
            `com.atproto.repo.describeRepo`. Returns a repository's handle, DID, DID document and the record
            collections it contains. A *repository* is one person's signed, content-addressed store of records;
            LogDate keeps entries, journals, links and drafts in it under the `studio.hypertext.logdate.*`
            collections.
            """,
        )
        request { repoParameter() }
        response {
            ok(
                "The repository's description.",
                DescribeRepoResponse(
                    handle = DocExamples.HANDLE,
                    did = did,
                    didDoc = IdentityDocs.didDocumentExample,
                    collections =
                        listOf(
                            Nsid.require("studio.hypertext.logdate.content"),
                            Nsid.require("studio.hypertext.logdate.journal"),
                            Nsid.require("studio.hypertext.logdate.association"),
                            Nsid.require("studio.hypertext.logdate.draft"),
                        ),
                    handleIsCorrect = true,
                ),
            )
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase("InvalidRequest", "`repo` is missing.", "Repo is required"),
                ErrorCase("RepoNotFound", "No repository here matches that DID or handle.", "Repo not found: willie.logdate.app"),
            )
        }
    }

    // ---- Sessions --------------------------------------------------------------------------------

    val createAccount: RouteConfig.() -> Unit = {
        publicOperation(
            "createAtprotoAccount",
            ApiTags.XRPC,
            "Create an AT Protocol account",
            """
            `com.atproto.server.createAccount`. Creates an account with a handle and password the AT Protocol
            way, and returns a session. LogDate's own apps use passkeys and **Authentication** instead; this
            exists so generic AT Protocol clients can sign up. The handle must be under one of the domains
            **Describe the PDS** lists.
            """,
        )
        request {
            jsonBody(
                CreateAccountRequest(email = DocExamples.EMAIL, handle = DocExamples.HANDLE, password = "correct horse battery staple"),
                "Handle, password and optional email. Invite codes and phone verification are not used here.",
            )
        }
        response {
            ok("The account exists and a session is open.", session)
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase("UnsupportedDomain", "The handle is not under a domain this server issues.", "Handle domain is not supported"),
                ErrorCase(
                    "InvalidPassword",
                    "The password does not meet the server's requirements.",
                    "Password does not satisfy server requirements",
                ),
                ErrorCase(
                    "InvalidRequest",
                    "The body is malformed or the handle is taken. The `message` says which.",
                    "Invalid session request",
                ),
            )
            pdsError(HttpStatusCode.Unauthorized, ErrorCase("InvalidToken", "The credentials were rejected.", "Authentication failed"))
            pdsError(
                HttpStatusCode.Forbidden,
                ErrorCase("AccountTakedown", "The account has been deactivated by the operator.", "Account is not active"),
            )
            notConfigured()
        }
    }

    val createSession: RouteConfig.() -> Unit = {
        publicOperation(
            "createAtprotoSession",
            ApiTags.XRPC,
            "Sign in with a password",
            """
            `com.atproto.server.createSession`. Signs in with a handle (or DID) and password and returns an
            `accessJwt` for requests and a `refreshJwt` for **Refresh the session**. This is AT Protocol's
            password login for generic clients; LogDate apps use passkeys.
            """,
        )
        request {
            jsonBody(
                CreateSessionRequest(identifier = DocExamples.HANDLE, password = "correct horse battery staple"),
                "Handle or DID, and password.",
            )
        }
        response {
            ok("Signed in. Send `accessJwt` as `Authorization: Bearer`.", session)
            pdsError(HttpStatusCode.BadRequest, ErrorCase("InvalidRequest", "The body is malformed.", "Invalid session request"))
            pdsError(HttpStatusCode.Unauthorized, ErrorCase("InvalidToken", "Wrong handle or password.", "Authentication failed"))
            pdsError(
                HttpStatusCode.Forbidden,
                ErrorCase("AccountTakedown", "The account has been deactivated by the operator.", "Account is not active"),
            )
            notConfigured()
        }
    }

    val getSession: RouteConfig.() -> Unit = {
        bearerOperation(
            "getAtprotoSession",
            ApiTags.XRPC,
            "Get the current session",
            """
            `com.atproto.server.getSession`. Returns the account behind an `accessJwt` sent as
            `Authorization: Bearer`. Use it to check whether a stored session is still valid.
            """,
        )
        response {
            ok(
                "The session's account.",
                SessionInfoResponse(
                    handle = DocExamples.HANDLE,
                    did = did,
                    email = DocExamples.EMAIL,
                    emailConfirmed = true,
                    active = true,
                ),
            )
            bearerUnauthorized(ErrorEnvelope.PDS)
            notConfigured()
        }
    }

    val refreshSession: RouteConfig.() -> Unit = {
        bearerOperation(
            "refreshAtprotoSession",
            ApiTags.XRPC,
            "Refresh the session",
            """
            `com.atproto.server.refreshSession`. Send the `refreshJwt` (not the access token) as
            `Authorization: Bearer` and get a new `accessJwt` and `refreshJwt`. Replace both; the old refresh
            token is spent.
            """,
        )
        response {
            ok("A new session. Replace both stored tokens.", session)
            bearerUnauthorized(ErrorEnvelope.PDS)
            notConfigured()
        }
    }

    val deleteSession: RouteConfig.() -> Unit = {
        bearerOperation(
            "deleteAtprotoSession",
            ApiTags.XRPC,
            "End the session",
            """
            `com.atproto.server.deleteSession`. Send the `refreshJwt` as `Authorization: Bearer` to revoke it.
            The access token expires on its own.
            """,
        )
        response {
            ok("The session is ended. The body is an empty object.", EmptyPdsResponse())
            bearerUnauthorized(ErrorEnvelope.PDS)
            notConfigured()
        }
    }

    // ---- Repository sync -----------------------------------------------------------------------

    val getRepo: RouteConfig.() -> Unit = {
        publicOperation(
            "getRepo",
            ApiTags.XRPC,
            "Export a repository",
            """
            `com.atproto.sync.getRepo`. Streams the whole repository as a CAR file (a content-addressed
            archive of all its blocks), which is how AT Protocol servers copy and verify each other's data.
            Pass `since` (a revision) to get only the blocks written after it.
            """,
        )
        request {
            didParameter()
            queryParameter<String>("since") {
                description = "Optional revision (TID) to export changes since. Omit for the full repository."
                required = false
                example("Example") { value = REV }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "The CAR archive."
                body<ByteArray> { mediaTypes(ContentType.parse("application/vnd.ipld.car")) }
            }
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase("InvalidRequest", "`did` is missing, or `did` or `since` is malformed.", "Invalid did or since"),
            )
            pdsError(HttpStatusCode.NotFound, repoNotFound)
            notConfigured()
        }
    }

    val getLatestCommit: RouteConfig.() -> Unit = {
        publicOperation(
            "getLatestCommit",
            ApiTags.XRPC,
            "Get the latest commit",
            """
            `com.atproto.sync.getLatestCommit`. Returns the CID and revision of the repository's newest commit.
            Compare it with what you last saw to learn whether anything changed before exporting.
            """,
        )
        request { didParameter() }
        response {
            ok("The head of the repository.", GetLatestCommitResponse(cid = Cid.require(COMMIT_CID), rev = Tid.require(REV)))
            pdsError(HttpStatusCode.BadRequest, ErrorCase("InvalidRequest", "`did` is missing or malformed.", "did is required"))
            pdsError(HttpStatusCode.NotFound, repoNotFound)
            notConfigured()
        }
    }

    val getRepoStatus: RouteConfig.() -> Unit = {
        publicOperation(
            "getRepoStatus",
            ApiTags.XRPC,
            "Get repository status",
            """
            `com.atproto.sync.getRepoStatus`. Says whether a repository is active on this server and, if so,
            its current revision. Deactivated or migrated repositories answer `active: false` with a `status`.
            """,
        )
        request { didParameter() }
        response {
            ok("The repository's status.", GetRepoStatusResponse(did = did, active = true, rev = Tid.require(REV)))
            pdsError(HttpStatusCode.BadRequest, ErrorCase("InvalidRequest", "`did` is missing or malformed.", "did is required"))
            pdsError(HttpStatusCode.NotFound, repoNotFound)
            notConfigured()
        }
    }

    // ---- Records --------------------------------------------------------------------------------

    val getRecord: RouteConfig.() -> Unit = {
        publicOperation(
            "getRecord",
            ApiTags.XRPC,
            "Get a record",
            """
            `com.atproto.repo.getRecord`. Fetches one record by repository, collection and record key. A
            *collection* is a lexicon ID such as `studio.hypertext.logdate.content`; the *record key* is the
            record's ID within it. Records are returned as the JSON the writer stored, plus the `at://` URI and
            the CID (content hash) of this version.
            """,
        )
        request {
            repoParameter()
            queryParameter<String>("collection") {
                description = "The collection's lexicon ID."
                required = true
                example("Example") { value = COLLECTION }
            }
            queryParameter<String>("rkey") {
                description = "The record key within the collection."
                required = true
                example("Example") { value = RKEY }
            }
            queryParameter<String>("cid") {
                description = "Optional CID; when given, the record is returned only if its current CID matches."
                required = false
                example("Example") { value = CID }
            }
        }
        response {
            ok("The record.", RepoRecord(uri = recordUri, cid = CID, value = record))
            pdsError(
                HttpStatusCode.BadRequest,
                invalidRequest,
                ErrorCase("RecordNotFound", "No record with that key exists in the collection.", "Record not found"),
                invalidSwap,
            )
            notConfigured()
        }
    }

    val listRecords: RouteConfig.() -> Unit = {
        publicOperation(
            "listRecords",
            ApiTags.XRPC,
            "List records",
            """
            `com.atproto.repo.listRecords`. Pages through the records in one collection of one repository.
            Pass the `cursor` from each response to get the next page; an absent cursor means you have reached
            the end.
            """,
        )
        request {
            repoParameter()
            queryParameter<String>("collection") {
                description = "The collection's lexicon ID."
                required = true
                example("Example") { value = COLLECTION }
            }
            queryParameter<Int>("limit") {
                description = "Records per page. Defaults to 50; clamped to 1…100."
                required = false
                example("Example") { value = 50 }
            }
            queryParameter<String>("cursor") {
                description = "The `cursor` from the previous page."
                required = false
                example("Example") { value = "3lcfmzdwnz22c" }
            }
            queryParameter<Boolean>("reverse") {
                description = "`true` to page from oldest to newest instead of newest to oldest."
                required = false
                example("Example") { value = false }
            }
        }
        response {
            ok(
                "A page of records.",
                ListRecordsResponse(records = listOf(RepoRecord(uri = recordUri, cid = CID, value = record)), cursor = null),
            )
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "InvalidRequest",
                    "`repo` or `collection` is missing or malformed, the collection is not one this server stores, or the cursor is invalid.",
                    "Invalid repo or collection",
                ),
            )
            notConfigured()
        }
    }

    val createRecord: RouteConfig.() -> Unit = {
        bearerOrDpopOperation(
            "createRecord",
            ApiTags.XRPC,
            "Create a record",
            """
            `com.atproto.repo.createRecord`. Writes a new record into a collection of your own repository
            and returns its URI and CID. Leave `rkey` out to let the server pick a key. Send `swapCommit` with
            the commit CID you last saw to fail instead of writing over a change you have not seen.

            Authenticate with either a LogDate access token (`Authorization: Bearer`) or an OAuth token
            (`Authorization: DPoP` plus a `DPoP` proof). The `repo` must be your own DID or handle.
            """,
        )
        request {
            jsonBody(
                CreateRecordInput(repo = DocExamples.DID, collection = Nsid.require(COLLECTION), record = record),
                "Where to write and what. `record` is the record's JSON, including `\$type`.",
            )
        }
        response {
            ok("The record was written.", RepoWriteResult(uri = recordUri, cid = CID, validationStatus = RepoValidationStatus.VALID))
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "InvalidRequest",
                    "The body is malformed, the repo is unknown, or the collection is not one this server stores.",
                    "Invalid repo",
                ),
                invalidSwap,
            )
            bearerUnauthorized(ErrorEnvelope.PDS)
            pdsError(HttpStatusCode.Forbidden, repoMismatch)
            notConfigured()
        }
    }

    val putRecord: RouteConfig.() -> Unit = {
        bearerOrDpopOperation(
            "putRecord",
            ApiTags.XRPC,
            "Create or replace a record",
            """
            `com.atproto.repo.putRecord`. Writes a record under a key you choose, creating or replacing it.
            `swapRecord` (the record's current CID) and `swapCommit` let you refuse to overwrite a version you
            have not seen. Same authentication and ownership rules as **Create a record**.
            """,
        )
        request {
            jsonBody(
                PutRecordInput(
                    repo = DocExamples.DID,
                    collection = Nsid.require(COLLECTION),
                    recordKey = RecordKey.require(RKEY),
                    record = record,
                    swapRecord = CID,
                ),
                "Where to write, the key, and the record's JSON.",
            )
        }
        response {
            ok("The record was written.", RepoWriteResult(uri = recordUri, cid = CID, validationStatus = RepoValidationStatus.VALID))
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "InvalidRequest",
                    "The body is malformed, the repo is unknown, or the collection is not one this server stores.",
                    "Invalid repo",
                ),
                invalidSwap,
            )
            bearerUnauthorized(ErrorEnvelope.PDS)
            pdsError(HttpStatusCode.Forbidden, repoMismatch)
            notConfigured()
        }
    }

    val deleteRecord: RouteConfig.() -> Unit = {
        bearerOrDpopOperation(
            "deleteRecord",
            ApiTags.XRPC,
            "Delete a record",
            """
            `com.atproto.repo.deleteRecord`. Removes a record from your repository. `swapRecord` and
            `swapCommit` work as in **Create or replace a record**. Deleting a record that does not exist
            succeeds.
            """,
        )
        request {
            jsonBody(
                DeleteRecordInput(repo = DocExamples.DID, collection = Nsid.require(COLLECTION), recordKey = RecordKey.require(RKEY)),
                "Which record to delete.",
            )
        }
        response {
            ok("The record is gone. The body is an empty object.", EmptyPdsResponse())
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase("InvalidRequest", "The body is malformed or the repo is unknown.", "Invalid repo"),
                invalidSwap,
            )
            bearerUnauthorized(ErrorEnvelope.PDS)
            pdsError(HttpStatusCode.Forbidden, repoMismatch)
            notConfigured()
        }
    }

    // ---- Blobs ----------------------------------------------------------------------------------

    val uploadBlob: RouteConfig.() -> Unit = {
        bearerOrDpopOperation(
            "uploadBlob",
            ApiTags.XRPC,
            "Upload a blob",
            """
            `com.atproto.repo.uploadBlob`. Stores a binary file (an image, say) in your repository and returns
            a *blob reference* to embed in a record. Send the raw bytes as the body with the file's
            `Content-Type`; there is no multipart wrapper. Blobs that no record references may be cleaned up.
            """,
        )
        request {
            headerParameter<String>("Content-Type") {
                description = "The blob's media type, such as `image/jpeg`. Defaults to `application/octet-stream`."
                required = false
                example("Example") { value = "image/jpeg" }
            }
            body<ByteArray> {
                description = "The file's bytes."
                required = true
                mediaTypes(ContentType.Application.OctetStream, ContentType.Image.Any)
            }
        }
        response {
            ok(
                "The blob is stored. Embed `blob` in a record to keep it.",
                UploadBlobResponse(blob = BlobRef(ref = CidLink(Cid.require(CID)), mimeType = "image/jpeg", size = 2_483_113)),
            )
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "InvalidRequest",
                    "The account has no DID yet, or the blob could not be stored.",
                    "Authenticated account does not have a valid DID",
                ),
            )
            bearerUnauthorized(ErrorEnvelope.PDS)
            notConfigured()
        }
    }

    val getBlob: RouteConfig.() -> Unit = {
        publicOperation(
            "getBlob",
            ApiTags.XRPC,
            "Download a blob",
            """
            `com.atproto.sync.getBlob`. Streams a blob's bytes by repository DID and CID, with the
            `Content-Type` it was uploaded with. Public, like the records that reference it.
            """,
        )
        request {
            didParameter()
            queryParameter<String>("cid") {
                description = "The blob's CID, from its blob reference."
                required = true
                example("Example") { value = CID }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "The blob's bytes. `Content-Type` is the type given at upload."
                body<ByteArray> { mediaTypes(ContentType.Application.OctetStream) }
            }
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase("InvalidRequest", "`did` or `cid` is missing or malformed.", "did and cid are required"),
            )
            pdsError(
                HttpStatusCode.NotFound,
                ErrorCase("BlobNotFound", "No blob with that CID exists in the repository.", "Blob not found"),
            )
            notConfigured()
        }
    }
}

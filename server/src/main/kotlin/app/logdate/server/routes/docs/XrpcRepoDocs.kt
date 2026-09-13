package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.CreateRecordInput
import app.logdate.server.routes.DeleteRecordInput
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.PutRecordInput
import app.logdate.server.routes.bearerOrDpopOperation
import app.logdate.server.routes.binarySchema
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.ok
import app.logdate.server.routes.pdsError
import app.logdate.server.routes.publicOperation
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import studio.hypertext.atproto.pds.BlobRef
import studio.hypertext.atproto.pds.CidLink
import studio.hypertext.atproto.pds.EmptyPdsResponse
import studio.hypertext.atproto.pds.GetLatestCommitResponse
import studio.hypertext.atproto.pds.GetRepoStatusResponse
import studio.hypertext.atproto.pds.ListRecordsResponse
import studio.hypertext.atproto.pds.UploadBlobResponse
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.repo.RepoRecord
import studio.hypertext.atproto.repo.RepoValidationStatus
import studio.hypertext.atproto.repo.RepoWriteResult
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey
import studio.hypertext.atproto.syntax.Tid

/**
 * Documentation for the repository, record and blob XRPC methods in `XrpcRoutes.kt`; identity and
 * session methods live in [XrpcDocs].
 */
internal object XrpcRepoDocs {
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
                body(binarySchema()) { mediaTypes(ContentType.parse("application/vnd.ipld.car")) }
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
                ErrorCase(
                    "RecordNotFound",
                    "No record with that key exists in the collection, or `cid` does not match it.",
                    "Record not found",
                ),
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
            and returns its URI and CID. Omit `rkey` to let the server choose a key. Send `swapCommit` with
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
                    "The repo is unknown or the collection is not one this server stores. A body that is not valid JSON for this method answers `400` with an empty body instead.",
                    "Invalid repo",
                ),
                invalidSwap,
            )
            authRequired()
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
                    "The repo is unknown or the collection is not one this server stores. A body that is not valid JSON for this method answers `400` with an empty body instead.",
                    "Invalid repo",
                ),
                invalidSwap,
            )
            authRequired()
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
                ErrorCase(
                    "InvalidRequest",
                    "The repo is unknown or not yours to write. A body that is not valid JSON for this method answers `400` with an empty body instead.",
                    "Invalid repo",
                ),
                invalidSwap,
            )
            authRequired()
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
            body(binarySchema()) {
                description =
                    "The file's bytes. The request's `Content-Type` (for example `image/jpeg`) is stored as the blob's media type " +
                    "and defaults to `application/octet-stream`."
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
            authRequired()
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
                body(binarySchema()) { mediaTypes(ContentType.Application.OctetStream) }
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

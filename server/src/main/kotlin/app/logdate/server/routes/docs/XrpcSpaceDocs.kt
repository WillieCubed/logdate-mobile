package app.logdate.server.routes.docs

import app.logdate.lexicon.app.logdate.crypto.Sealed
import app.logdate.lexicon.app.logdate.space.DeleteRecordOutput
import app.logdate.lexicon.app.logdate.space.GetRecordOutput
import app.logdate.lexicon.app.logdate.space.ListRepoOpsChange
import app.logdate.lexicon.app.logdate.space.ListRepoOpsOutput
import app.logdate.lexicon.app.logdate.space.PutRecordOutput
import app.logdate.lexicon.app.logdate.space.UploadBlobOutput
import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.SpaceDeleteRecordInput
import app.logdate.server.routes.SpacePutRecordInput
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.binarySchema
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.ok
import app.logdate.server.routes.pdsError
import io.github.smiley4.ktoropenapi.config.RequestConfig
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import studio.hypertext.atproto.lexicon.data.Blob
import studio.hypertext.atproto.lexicon.data.Bytes
import studio.hypertext.atproto.lexicon.data.CidLink
import studio.hypertext.atproto.repo.Cid

/** Documentation for the personal space methods in `SpaceXrpcRoutes.kt`. */
internal object XrpcSpaceDocs {
    private const val SPACE = "at://${DocExamples.DID}/space/app.logdate.space.personal/self"
    private const val SEALED_RKEY = "k7ovz5m7eiqdxu6yi3"
    private const val BLOB_CID = "bafkreihdwdcefgh4dqkjv67uzcmw7ojee6xedzdetojuzjevtenxquvyku"
    private val sealed = Sealed(ciphertext = Bytes(ByteArray(528)), keyId = "ck-2026-09", nonce = Bytes(ByteArray(12)), version = 1)

    private const val CID_SHAPE =
        "A CID-shaped value starts with `b`, the lowercase base32 prefix, and is at most 255 characters; the server " +
            "doesn't decode it further."

    private const val OWNER_ONLY =
        "Only the space's owner can use it, from their own LogDate devices: send the LogDate access token from " +
            "**Authentication** as `Authorization: Bearer`. Any other space, including another account's, answers " +
            "exactly as a missing record does, so nobody learns whether it exists."

    private val recordNotFound =
        ErrorCase(
            "RecordNotFound",
            "No live record has that key, or the space isn't yours. The two cases answer identically.",
            "Not found",
        )
    private val blobNotFound =
        ErrorCase(
            "BlobNotFound",
            "No blob with that CID is in the space, or the space isn't yours. The two cases answer identically.",
            "Not found",
        )
    private val logDateTokenRequired =
        ErrorCase(
            "AuthRequired",
            "No LogDate access token was sent, or it is invalid or expired. OAuth DPoP tokens, AT Protocol session " +
                "tokens and linked device tokens are refused the same way: personal spaces are only for the owner's " +
                "own LogDate devices. Refresh the access token and retry once; if that also fails, sign in again.",
            "A LogDate access token is required",
        )
    private val invalidSpace = ErrorCase("InvalidRequest", "`space` is missing or isn't a space URI.", "space must be a space URI")
    private val invalidRecord =
        ErrorCase(
            "InvalidRecord",
            "The record isn't a padded `app.logdate.crypto.sealed` envelope, or it refers to a blob the space doesn't hold. " +
                "The `message` says which.",
            "ciphertext isn't padded to a size bucket",
        )
    private val swapConflict =
        ErrorCase(
            "InvalidSwap",
            "`swapCid` or `createOnly` no longer matches the stored record: another device wrote in between. Read it again and retry.",
            "The record changed",
        )
    private val notEnabled =
        ErrorCase(
            "MethodNotImplemented",
            "This server doesn't offer personal spaces. Check for `personalSpaceV1` in **Describe this server**.",
            "Personal spaces aren't enabled on this server",
        )

    val putRecord: RouteConfig.() -> Unit = {
        bearerOperation(
            "putSpaceRecord",
            ApiTags.XRPC,
            "Store one sealed record in your personal space",
            """
            `app.logdate.space.putRecord`. Creates or replaces one record in your personal space. Every record
            there is an `app.logdate.crypto.sealed` envelope whose ciphertext is padded to a size bucket, so the
            server stores it without being able to read it. Each write gets the next sequence number, which
            **List changes in your personal space** pages through. $OWNER_ONLY
            """,
        )
        request {
            jsonBody(
                SpacePutRecordInput(
                    space = SPACE,
                    rkey = SEALED_RKEY,
                    record = Json.encodeToJsonElement(sealed).jsonObject,
                    blobs = listOf(BLOB_CID),
                ),
                "The record, its key, the blobs it refers to, and an optional `swapCid` or `createOnly` condition.",
            )
        }
        response {
            ok("The record is stored.", PutRecordOutput(cid = CID, seq = 42))
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "InvalidRequest",
                    "The body isn't valid JSON input, `space` isn't a space URI, `rkey` isn't a record key, a value in `blobs` or " +
                        "`swapCid` isn't CID-shaped, or `blobs` lists more than 64 CIDs. The `message` says which. $CID_SHAPE",
                    "rkey must be a record key",
                ),
                invalidRecord,
                recordNotFound,
            )
            pdsError(HttpStatusCode.Unauthorized, logDateTokenRequired)
            pdsError(HttpStatusCode.Conflict, swapConflict)
            pdsError(
                HttpStatusCode.PayloadTooLarge,
                ErrorCase("RecordTooLarge", "The request is larger than the largest sealed record can be.", "The record is too large"),
            )
            pdsError(HttpStatusCode.NotImplemented, notEnabled)
        }
    }

    val deleteRecord: RouteConfig.() -> Unit = {
        bearerOperation(
            "deleteSpaceRecord",
            ApiTags.XRPC,
            "Delete a record from your personal space",
            """
            `app.logdate.space.deleteRecord`. Replaces a record with a deletion marker that other devices see
            in **List changes in your personal space**. Deleting a key that's already gone changes nothing and
            answers the latest sequence number. $OWNER_ONLY
            """,
        )
        request {
            jsonBody(
                SpaceDeleteRecordInput(space = SPACE, rkey = SEALED_RKEY, swapCid = CID),
                "The record's key and an optional `swapCid`.",
            )
        }
        response {
            ok("The record is deleted.", DeleteRecordOutput(seq = 43))
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "InvalidRequest",
                    "The body isn't valid JSON input, `space` isn't a space URI, `rkey` isn't a record key, or `swapCid` isn't " +
                        "CID-shaped. $CID_SHAPE",
                    "rkey must be a record key",
                ),
                recordNotFound,
            )
            pdsError(HttpStatusCode.Unauthorized, logDateTokenRequired)
            pdsError(HttpStatusCode.Conflict, swapConflict)
            pdsError(HttpStatusCode.NotImplemented, notEnabled)
        }
    }

    val getRecord: RouteConfig.() -> Unit = {
        bearerOperation(
            "getSpaceRecord",
            ApiTags.XRPC,
            "Read a record from your personal space",
            """
            `app.logdate.space.getRecord`. Returns one sealed record with its CID and the sequence number of
            its latest write. Decrypt it on the device with the owner's content key. $OWNER_ONLY
            """,
        )
        request {
            spaceParameter()
            queryParameter<String>("rkey") {
                description = "The sealed record's key."
                required = true
                example("Example") { value = SEALED_RKEY }
            }
        }
        response {
            ok("The sealed record.", GetRecordOutput(cid = CID, seq = 42, value = sealed))
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "InvalidRequest",
                    "`space` is missing or isn't a space URI, or `rkey` is missing or isn't a record key.",
                    "rkey must be a record key",
                ),
                recordNotFound,
            )
            pdsError(HttpStatusCode.Unauthorized, logDateTokenRequired)
            pdsError(HttpStatusCode.NotImplemented, notEnabled)
        }
    }

    val listRepoOps: RouteConfig.() -> Unit = {
        bearerOperation(
            "listSpaceChanges",
            ApiTags.XRPC,
            "List changes in your personal space",
            """
            `app.logdate.space.listRepoOps`. Lists every write and deletion after a sequence number, oldest
            first, with only the latest state of each key. Send `since=0` to export the whole space, then the
            `cursor` of each page while `hasMore` is true. A page ends before `limit` changes rather than carry
            more than 8 MiB of stored records, but always holds at least one change. $OWNER_ONLY
            """,
        )
        request {
            spaceParameter()
            queryParameter<Long>("since") {
                description = "Return changes after this sequence number. Defaults to 0, the start of the space."
                required = false
                example("First page") { value = 0L }
            }
            queryParameter<Int>("limit") {
                description = "The most changes to return. Defaults to 200; values outside 1 to 500 are clamped."
                required = false
                example("Default") { value = 200 }
            }
        }
        response {
            ok(
                "A page of changes.",
                ListRepoOpsOutput(
                    cursor = 43,
                    hasMore = false,
                    ops =
                        listOf(
                            ListRepoOpsChange(cid = CID, deleted = false, rkey = SEALED_RKEY, seq = 42, value = sealed),
                            ListRepoOpsChange(deleted = true, rkey = "p3xq2mzb6t4w5yhe0c", seq = 43),
                        ),
                ),
            )
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "InvalidRequest",
                    "`space` is malformed, or `since` or `limit` isn't a whole number.",
                    "limit must be a whole number",
                ),
                recordNotFound,
            )
            pdsError(HttpStatusCode.Unauthorized, logDateTokenRequired)
            pdsError(HttpStatusCode.NotImplemented, notEnabled)
        }
    }

    val uploadBlob: RouteConfig.() -> Unit = {
        bearerOperation(
            "uploadSpaceBlob",
            ApiTags.XRPC,
            "Upload an encrypted file to your personal space",
            """
            `app.logdate.space.uploadBlob`. Stores an encrypted file, such as a photo sealed on the device, and
            returns the blob reference to keep inside a sealed record. Send the raw bytes as the body. List the
            CID in `blobs` when you store the record: blobs that no record refers to are deleted by periodic
            cleanup. Uploading bytes the space already holds stores them once. Only the space's owner can upload,
            with a LogDate access token: any other space answers `BlobNotFound`.
            """,
        )
        request {
            spaceParameter()
            body(binarySchema()) {
                description = "The encrypted file's bytes, at most 100 MiB (104,857,600 bytes)."
                required = true
                mediaTypes(ContentType.Application.OctetStream)
            }
        }
        response {
            ok(
                "The blob is stored. Its CID is the hash of the bytes sent.",
                UploadBlobOutput(
                    blob = Blob(ref = CidLink(cid = Cid.require(BLOB_CID)), mimeType = "application/octet-stream", size = 2_483_113),
                ),
            )
            pdsError(HttpStatusCode.BadRequest, invalidSpace, blobNotFound)
            pdsError(HttpStatusCode.Unauthorized, logDateTokenRequired)
            pdsError(
                HttpStatusCode.PayloadTooLarge,
                ErrorCase("BlobTooLarge", "The file is larger than 100 MiB.", "Blobs can be at most 104857600 bytes"),
            )
            pdsError(HttpStatusCode.NotImplemented, notEnabled)
        }
    }

    val getBlob: RouteConfig.() -> Unit = {
        bearerOperation(
            "getSpaceBlob",
            ApiTags.XRPC,
            "Download an encrypted file from your personal space",
            """
            `app.logdate.space.getBlob`. Returns the encrypted bytes of a blob in your personal space, to
            decrypt on the device. Only the space's owner can download, with a LogDate access token: any other
            space answers exactly as a missing blob does.
            """,
        )
        request {
            spaceParameter()
            queryParameter<String>("cid") {
                description = "The blob's CID, from its blob reference."
                required = true
                example("Example") { value = BLOB_CID }
            }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "The encrypted bytes."
                body(binarySchema()) { mediaTypes(ContentType.Application.OctetStream) }
            }
            pdsError(
                HttpStatusCode.BadRequest,
                ErrorCase(
                    "InvalidRequest",
                    "`space` is missing or isn't a space URI, or `cid` is missing or isn't CID-shaped. $CID_SHAPE",
                    "cid must be CID-shaped",
                ),
                blobNotFound,
            )
            pdsError(HttpStatusCode.Unauthorized, logDateTokenRequired)
            pdsError(HttpStatusCode.NotImplemented, notEnabled)
        }
    }

    private fun RequestConfig.spaceParameter() {
        queryParameter<String>("space") {
            description = "Your personal space's URI: `at://{your DID}/space/app.logdate.space.personal/self`."
            required = true
            example("Example") { value = SPACE }
        }
    }
}

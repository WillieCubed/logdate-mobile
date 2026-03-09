package studio.hypertext.atproto.pds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.RepoListPage
import studio.hypertext.atproto.repo.RepoRecord
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey

/**
 * Error response used by current PDS and XRPC route surfaces.
 */
@Serializable
public data class PdsErrorResponse(
    val error: String,
    val message: String,
)

/**
 * Request for an exact repository record.
 */
@Serializable
public data class GetRecordRequest(
    val repo: AtprotoDid,
    val collection: Nsid,
    val recordKey: RecordKey,
    val cid: String? = null,
)

/**
 * Request for a repository record page.
 */
@Serializable
public data class ListRecordsRequest(
    val repo: AtprotoDid,
    val collection: Nsid,
    val limit: Int = DEFAULT_PAGE_SIZE,
    val cursor: String? = null,
    val reverse: Boolean = false,
) {
    public companion object {
        /**
         * Default list-records page size.
         */
        public const val DEFAULT_PAGE_SIZE: Int = 50
    }
}

/**
 * Request to create a repository record.
 */
@Serializable
public data class CreateRecordRequest(
    val repo: AtprotoDid,
    val collection: Nsid,
    val record: JsonObject,
    @SerialName("rkey")
    val recordKey: RecordKey? = null,
    val validate: Boolean? = null,
    val swapCommit: String? = null,
)

/**
 * Request to replace a repository record.
 */
@Serializable
public data class PutRecordRequest(
    val repo: AtprotoDid,
    val collection: Nsid,
    @SerialName("rkey")
    val recordKey: RecordKey,
    val record: JsonObject,
    val validate: Boolean? = null,
    val swapRecord: String? = null,
    val swapCommit: String? = null,
)

/**
 * Request to delete a repository record.
 */
@Serializable
public data class DeleteRecordRequest(
    val repo: AtprotoDid,
    val collection: Nsid,
    @SerialName("rkey")
    val recordKey: RecordKey,
    val swapRecord: String? = null,
    val swapCommit: String? = null,
)

/**
 * Response wrapper for list-records route output.
 */
@Serializable
public data class ListRecordsResponse(
    val records: List<RepoRecord>,
    val cursor: String? = null,
) {
    public companion object {
        /**
         * Builds a wire response from a repo page.
         */
        public fun fromPage(page: RepoListPage): ListRecordsResponse =
            ListRecordsResponse(
                records = page.records,
                cursor = page.cursor,
            )
    }
}

/**
 * Empty body returned by no-content style procedure routes.
 */
@Serializable
public class EmptyPdsResponse

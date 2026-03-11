package studio.hypertext.atproto.pds

import kotlinx.serialization.Serializable
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.repo.Cid
import studio.hypertext.atproto.syntax.Tid

/**
 * Request payload for `com.atproto.sync.getRepo`.
 */
@Serializable
public data class GetRepoRequest(
    val did: AtprotoDid,
    val since: Tid? = null,
)

/**
 * Raw repository export returned by `com.atproto.sync.getRepo`.
 */
public data class RepoExportResponse(
    val contentType: String,
    val bytes: ByteArray,
)

/**
 * Request payload for `com.atproto.sync.getLatestCommit`.
 */
@Serializable
public data class GetLatestCommitRequest(
    val did: AtprotoDid,
)

/**
 * Response payload for `com.atproto.sync.getLatestCommit`.
 */
@Serializable
public data class GetLatestCommitResponse(
    val cid: Cid,
    val rev: Tid,
)

/**
 * Request payload for `com.atproto.sync.getRepoStatus`.
 */
@Serializable
public data class GetRepoStatusRequest(
    val did: AtprotoDid,
)

/**
 * Response payload for `com.atproto.sync.getRepoStatus`.
 */
@Serializable
public data class GetRepoStatusResponse(
    val did: AtprotoDid,
    val active: Boolean,
    val status: String? = null,
    val rev: Tid? = null,
)

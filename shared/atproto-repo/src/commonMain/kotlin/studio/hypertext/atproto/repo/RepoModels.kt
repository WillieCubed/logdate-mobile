package studio.hypertext.atproto.repo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.syntax.AtUri
import studio.hypertext.atproto.syntax.Nsid
import studio.hypertext.atproto.syntax.RecordKey

/**
 * Stable identifier for a single record inside a repository collection.
 */
@Serializable
public data class RepoRecordId(
    val repo: AtprotoDid,
    val collection: Nsid,
    val recordKey: RecordKey,
) {
    /**
     * Canonical AT URI for this record.
     */
    public val uri: AtUri
        get() = AtUri.require("at://$repo/$collection/$recordKey")
}

/**
 * Standalone repository record payload and metadata.
 */
@Serializable
public data class RepoRecord(
    val uri: AtUri,
    val cid: String? = null,
    val value: JsonObject,
)

/**
 * Page of repository records returned by list-style operations.
 */
@Serializable
public data class RepoListPage(
    val records: List<RepoRecord>,
    val cursor: String? = null,
)

/**
 * Validation status surfaced by write operations when a store cannot fully
 * validate a record against its lexicon.
 */
@Serializable
public enum class RepoValidationStatus {
    /**
     * Record shape was accepted as valid by the active store.
     */
    @SerialName("valid")
    VALID,

    /**
     * The active store accepted the record but did not run full validation.
     */
    @SerialName("unknown")
    UNKNOWN,
}

/**
 * Result of a successful repository write.
 */
@Serializable
public data class RepoWriteResult(
    val uri: AtUri,
    val cid: String,
    val validationStatus: RepoValidationStatus = RepoValidationStatus.UNKNOWN,
)

/**
 * Raw block stored inside a repo export or block store.
 */
public data class RepoBlock(
    val cid: Cid,
    val bytes: ByteArray,
)

/**
 * Canonical repo head metadata.
 */
public data class RepoHead(
    val repo: AtprotoDid,
    val root: Cid,
    val commitCid: Cid,
    val revision: Long,
)

/**
 * Unsigned repo commit payload.
 */
@Serializable
public data class RepoCommit(
    val repo: AtprotoDid,
    val root: Cid,
    val prev: Cid? = null,
    val revision: Long,
    val createdAtEpochMillis: Long,
    val recordCount: Int,
)

/**
 * Signed repo commit entry.
 */
public data class SignedRepoCommit(
    val cid: Cid,
    val commit: RepoCommit,
    val signature: String,
)

/**
 * Exported repo state that can be serialized into a CAR-like archive.
 */
public data class RepoExport(
    val repo: AtprotoDid,
    val head: RepoHead,
    val commits: List<SignedRepoCommit>,
    val blocks: List<RepoBlock>,
)

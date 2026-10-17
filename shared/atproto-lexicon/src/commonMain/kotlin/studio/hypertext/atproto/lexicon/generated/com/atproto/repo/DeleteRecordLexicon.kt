package studio.hypertext.atproto.lexicon.generated.com.atproto.repo

import kotlinx.serialization.Serializable

public object DeleteRecordLexicon {
    public const val ID: String = "com.atproto.repo.deleteRecord"
}

@Serializable
public data class DeleteRecordInput(
    /** The NSID of the record collection. */
    val collection: String,
    /** The handle or DID of the repo (aka, current account). */
    val repo: String,
    /** The Record Key. */
    val rkey: String,
    /** Compare and swap with the previous commit by CID. */
    val swapCommit: String? = null,
    /** Compare and swap with the previous record by CID. */
    val swapRecord: String? = null,
)

@Serializable
public data class DeleteRecordOutput(
    val commit: DefsCommitMeta? = null,
)

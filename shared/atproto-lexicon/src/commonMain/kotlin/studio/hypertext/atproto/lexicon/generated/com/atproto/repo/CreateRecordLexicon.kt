package studio.hypertext.atproto.lexicon.generated.com.atproto.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object CreateRecordLexicon {
    public const val ID: String = "com.atproto.repo.createRecord"
}

@Serializable
public data class CreateRecordInput(
    /** The NSID of the record collection. */
    val collection: String,
    /** The record itself. Must contain a $type field. */
    val record: JsonElement,
    /** The handle or DID of the repo (aka, current account). */
    val repo: String,
    /** The Record Key. */
    val rkey: String? = null,
    /** Compare and swap with the previous commit by CID. */
    val swapCommit: String? = null,
    /**
     * Can be set to 'false' to skip Lexicon schema validation of record data, 'true' to require it, or leave unset to
     * validate only for known Lexicons.
     */
    val validate: Boolean? = null,
)

@Serializable
public data class CreateRecordOutput(
    val cid: String,
    val commit: DefsCommitMeta? = null,
    val uri: String,
    val validationStatus: String? = null,
)

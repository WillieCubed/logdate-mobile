package studio.hypertext.atproto.lexicon.generated.com.atproto.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object GetRecordLexicon {
    public const val ID: String = "com.atproto.repo.getRecord"
}

@Serializable
public data class GetRecordParams(
    /** The CID of the version of the record. If not specified, then return the most recent version. */
    val cid: String? = null,
    /** The NSID of the record collection. */
    val collection: String,
    /** The handle or DID of the repo. */
    val repo: String,
    /** The Record Key. */
    val rkey: String,
)

@Serializable
public data class GetRecordOutput(
    val cid: String? = null,
    val uri: String,
    val value: JsonElement,
)

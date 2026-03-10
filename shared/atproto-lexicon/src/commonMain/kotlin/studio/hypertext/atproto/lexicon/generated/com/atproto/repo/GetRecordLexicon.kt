package studio.hypertext.atproto.lexicon.generated.com.atproto.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object GetRecordLexicon {
    public const val ID: String = "com.atproto.repo.getRecord"
}

@Serializable
public data class GetRecordParams(
    val cid: String?,
    val collection: String,
    val repo: String,
    val rkey: String,
)

@Serializable
public data class GetRecordOutput(
    val cid: String?,
    val uri: String,
    val value: JsonElement,
)

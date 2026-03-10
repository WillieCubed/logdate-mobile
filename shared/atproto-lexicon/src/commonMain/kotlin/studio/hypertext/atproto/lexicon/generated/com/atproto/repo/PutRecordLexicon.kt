package studio.hypertext.atproto.lexicon.generated.com.atproto.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object PutRecordLexicon {
    public const val ID: String = "com.atproto.repo.putRecord"
}

@Serializable
public data class PutRecordInput(
    val collection: String,
    val record: JsonElement,
    val repo: String,
    val rkey: String,
    val swapCommit: String?,
    val swapRecord: String?,
    val validate: Boolean?,
)

@Serializable
public data class PutRecordOutput(
    val cid: String,
    val commit: DefsCommitMeta?,
    val uri: String,
    val validationStatus: String?,
)

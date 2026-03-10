package studio.hypertext.atproto.lexicon.generated.com.atproto.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object CreateRecordLexicon {
    public const val ID: String = "com.atproto.repo.createRecord"
}

@Serializable
public data class CreateRecordInput(
    val collection: String,
    val record: JsonElement,
    val repo: String,
    val rkey: String?,
    val swapCommit: String?,
    val validate: Boolean?,
)

@Serializable
public data class CreateRecordOutput(
    val cid: String,
    val commit: DefsCommitMeta?,
    val uri: String,
    val validationStatus: String?,
)

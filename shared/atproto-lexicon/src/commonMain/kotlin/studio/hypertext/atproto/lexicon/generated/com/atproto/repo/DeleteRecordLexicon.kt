package studio.hypertext.atproto.lexicon.generated.com.atproto.repo

import kotlinx.serialization.Serializable

public object DeleteRecordLexicon {
    public const val ID: String = "com.atproto.repo.deleteRecord"
}

@Serializable
public data class DeleteRecordInput(
    val collection: String,
    val repo: String,
    val rkey: String,
    val swapCommit: String?,
    val swapRecord: String?,
)

@Serializable
public data class DeleteRecordOutput(
    val commit: DefsCommitMeta?,
)

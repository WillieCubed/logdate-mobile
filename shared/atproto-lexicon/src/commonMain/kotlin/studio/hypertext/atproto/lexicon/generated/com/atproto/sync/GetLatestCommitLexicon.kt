package studio.hypertext.atproto.lexicon.generated.com.atproto.sync

import kotlinx.serialization.Serializable

public object GetLatestCommitLexicon {
    public const val ID: String = "com.atproto.sync.getLatestCommit"
}

@Serializable
public data class GetLatestCommitParams(
    /** The DID of the repo. */
    val did: String,
)

@Serializable
public data class GetLatestCommitOutput(
    val cid: String,
    val rev: String,
)

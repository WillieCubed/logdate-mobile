package studio.hypertext.atproto.lexicon.generated.com.atproto.sync

import kotlinx.serialization.Serializable

public object GetRepoStatusLexicon {
    public const val ID: String = "com.atproto.sync.getRepoStatus"
}

@Serializable
public data class GetRepoStatusParams(
    val did: String,
)

@Serializable
public data class GetRepoStatusOutput(
    val active: Boolean,
    val did: String,
    val rev: String?,
    val status: String?,
)

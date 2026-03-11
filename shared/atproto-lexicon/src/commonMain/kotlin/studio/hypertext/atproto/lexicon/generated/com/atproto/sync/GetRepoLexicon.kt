package studio.hypertext.atproto.lexicon.generated.com.atproto.sync

import kotlinx.serialization.Serializable

public object GetRepoLexicon {
    public const val ID: String = "com.atproto.sync.getRepo"
}

@Serializable
public data class GetRepoParams(
    val did: String,
    val since: String?,
)

package studio.hypertext.atproto.lexicon.generated.com.atproto.sync

import kotlinx.serialization.Serializable

public object GetRepoLexicon {
    public const val ID: String = "com.atproto.sync.getRepo"
}

@Serializable
public data class GetRepoParams(
    /** The DID of the repo. */
    val did: String,
    /** The revision ('rev') of the repo to create a diff from. */
    val since: String? = null,
)

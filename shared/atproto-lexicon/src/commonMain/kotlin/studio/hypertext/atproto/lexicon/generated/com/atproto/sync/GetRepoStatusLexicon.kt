package studio.hypertext.atproto.lexicon.generated.com.atproto.sync

import kotlinx.serialization.Serializable

public object GetRepoStatusLexicon {
    public const val ID: String = "com.atproto.sync.getRepoStatus"
}

@Serializable
public data class GetRepoStatusParams(
    /** The DID of the repo. */
    val did: String,
)

@Serializable
public data class GetRepoStatusOutput(
    val active: Boolean,
    val did: String,
    /** Optional field, the current rev of the repo, if active=true */
    val rev: String? = null,
    /**
     * If active=false, this optional field indicates a possible reason for why the account is not active. If
     * active=false and no status is supplied, then the host makes no claim for why the repository is no longer being
     * hosted.
     */
    val status: String? = null,
)

package studio.hypertext.atproto.lexicon.generated.com.atproto.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object GetSessionLexicon {
    public const val ID: String = "com.atproto.server.getSession"
}

@Serializable
public data class GetSessionOutput(
    val active: Boolean? = null,
    val did: String,
    val didDoc: JsonElement? = null,
    val email: String? = null,
    val emailAuthFactor: Boolean? = null,
    val emailConfirmed: Boolean? = null,
    val handle: String,
    /**
     * If active=false, this optional field indicates a possible reason for why the account is not active. If
     * active=false and no status is supplied, then the host makes no claim for why the repository is no longer being
     * hosted.
     */
    val status: String? = null,
)

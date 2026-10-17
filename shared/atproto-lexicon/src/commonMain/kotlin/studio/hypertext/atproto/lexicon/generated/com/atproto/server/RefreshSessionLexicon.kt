package studio.hypertext.atproto.lexicon.generated.com.atproto.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object RefreshSessionLexicon {
    public const val ID: String = "com.atproto.server.refreshSession"
}

@Serializable
public data class RefreshSessionOutput(
    val accessJwt: String,
    val active: Boolean? = null,
    val did: String,
    val didDoc: JsonElement? = null,
    val email: String? = null,
    val emailAuthFactor: Boolean? = null,
    val emailConfirmed: Boolean? = null,
    val handle: String,
    val refreshJwt: String,
    /** Hosting status of the account. If not specified, then assume 'active'. */
    val status: String? = null,
)

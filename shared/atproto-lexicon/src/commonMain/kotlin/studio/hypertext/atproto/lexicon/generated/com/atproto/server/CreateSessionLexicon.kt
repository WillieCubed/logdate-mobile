package studio.hypertext.atproto.lexicon.generated.com.atproto.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object CreateSessionLexicon {
    public const val ID: String = "com.atproto.server.createSession"
}

@Serializable
public data class CreateSessionInput(
    /**
     * When true, instead of throwing error for takendown accounts, a valid response with a narrow scoped token will be
     * returned
     */
    val allowTakendown: Boolean? = null,
    val authFactorToken: String? = null,
    /** Handle or other identifier supported by the server for the authenticating user. */
    val identifier: String,
    val password: String,
)

@Serializable
public data class CreateSessionOutput(
    val accessJwt: String,
    val active: Boolean? = null,
    val did: String,
    val didDoc: JsonElement? = null,
    val email: String? = null,
    val emailAuthFactor: Boolean? = null,
    val emailConfirmed: Boolean? = null,
    val handle: String,
    val refreshJwt: String,
    /**
     * If active=false, this optional field indicates a possible reason for why the account is not active. If
     * active=false and no status is supplied, then the host makes no claim for why the repository is no longer being
     * hosted.
     */
    val status: String? = null,
)

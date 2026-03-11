package studio.hypertext.atproto.lexicon.generated.com.atproto.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object CreateSessionLexicon {
    public const val ID: String = "com.atproto.server.createSession"
}

@Serializable
public data class CreateSessionInput(
    val allowTakendown: Boolean?,
    val authFactorToken: String?,
    val identifier: String,
    val password: String,
)

@Serializable
public data class CreateSessionOutput(
    val accessJwt: String,
    val active: Boolean?,
    val did: String,
    val didDoc: JsonElement?,
    val email: String?,
    val emailAuthFactor: Boolean?,
    val emailConfirmed: Boolean?,
    val handle: String,
    val refreshJwt: String,
    val status: String?,
)

package studio.hypertext.atproto.lexicon.generated.com.atproto.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object RefreshSessionLexicon {
    public const val ID: String = "com.atproto.server.refreshSession"
}

@Serializable
public data class RefreshSessionOutput(
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

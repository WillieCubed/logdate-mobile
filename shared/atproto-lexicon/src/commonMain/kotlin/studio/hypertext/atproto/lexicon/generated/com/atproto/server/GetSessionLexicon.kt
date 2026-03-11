package studio.hypertext.atproto.lexicon.generated.com.atproto.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object GetSessionLexicon {
    public const val ID: String = "com.atproto.server.getSession"
}

@Serializable
public data class GetSessionOutput(
    val active: Boolean?,
    val did: String,
    val didDoc: JsonElement?,
    val email: String?,
    val emailAuthFactor: Boolean?,
    val emailConfirmed: Boolean?,
    val handle: String,
    val status: String?,
)

package studio.hypertext.atproto.lexicon.generated.com.atproto.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object CreateAccountLexicon {
    public const val ID: String = "com.atproto.server.createAccount"
}

@Serializable
public data class CreateAccountInput(
    val did: String?,
    val email: String?,
    val handle: String,
    val inviteCode: String?,
    val password: String?,
    val plcOp: JsonElement?,
    val recoveryKey: String?,
    val verificationCode: String?,
    val verificationPhone: String?,
)

@Serializable
public data class CreateAccountOutput(
    val accessJwt: String,
    val did: String,
    val didDoc: JsonElement?,
    val handle: String,
    val refreshJwt: String,
)

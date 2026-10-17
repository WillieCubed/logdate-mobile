package studio.hypertext.atproto.lexicon.generated.com.atproto.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object CreateAccountLexicon {
    public const val ID: String = "com.atproto.server.createAccount"
}

@Serializable
public data class CreateAccountInput(
    /** Pre-existing atproto DID, being imported to a new account. */
    val did: String? = null,
    val email: String? = null,
    /** Requested handle for the account. */
    val handle: String,
    val inviteCode: String? = null,
    /** Initial account password. May need to meet instance-specific password strength requirements. */
    val password: String? = null,
    /**
     * A signed DID PLC operation to be submitted as part of importing an existing account to this instance. NOTE: this
     * optional field may be updated when full account migration is implemented.
     */
    val plcOp: JsonElement? = null,
    /** DID PLC rotation key (aka, recovery key) to be included in PLC creation operation. */
    val recoveryKey: String? = null,
    val verificationCode: String? = null,
    val verificationPhone: String? = null,
)

/** Account login session returned on successful account creation. */
@Serializable
public data class CreateAccountOutput(
    val accessJwt: String,
    /** The DID of the new account. */
    val did: String,
    /** Complete DID document. */
    val didDoc: JsonElement? = null,
    val handle: String,
    val refreshJwt: String,
)

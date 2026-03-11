package studio.hypertext.atproto.pds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.DidDocument

/**
 * Request payload for `com.atproto.server.createAccount`.
 */
@Serializable
public data class CreateAccountRequest(
    val email: String? = null,
    val handle: String,
    val did: String? = null,
    val inviteCode: String? = null,
    val verificationCode: String? = null,
    val verificationPhone: String? = null,
    val password: String? = null,
    val recoveryKey: String? = null,
    val plcOp: String? = null,
)

/**
 * Request payload for `com.atproto.server.createSession`.
 */
@Serializable
public data class CreateSessionRequest(
    val identifier: String,
    val password: String,
    val authFactorToken: String? = null,
    val allowTakendown: Boolean? = null,
)

/**
 * Common session fields returned by the standard AT Protocol server session routes.
 */
@Serializable
public data class SessionInfoResponse(
    val handle: String,
    val did: AtprotoDid,
    val didDoc: DidDocument? = null,
    val email: String? = null,
    val emailConfirmed: Boolean? = null,
    val emailAuthFactor: Boolean? = null,
    val active: Boolean? = null,
    val status: String? = null,
)

/**
 * Authenticated session returned by `createAccount`, `createSession`, and `refreshSession`.
 */
@Serializable
public data class SessionResponse(
    @SerialName("accessJwt")
    val accessJwt: String,
    @SerialName("refreshJwt")
    val refreshJwt: String,
    val handle: String,
    val did: AtprotoDid,
    val didDoc: DidDocument? = null,
    val email: String? = null,
    val emailConfirmed: Boolean? = null,
    val emailAuthFactor: Boolean? = null,
    val active: Boolean? = null,
    val status: String? = null,
)

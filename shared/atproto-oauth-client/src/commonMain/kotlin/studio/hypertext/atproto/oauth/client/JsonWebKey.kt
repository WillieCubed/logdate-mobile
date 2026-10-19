package studio.hypertext.atproto.oauth.client

import kotlinx.serialization.Serializable

/**
 * An elliptic-curve JSON Web Key (RFC 7517) as used by AT Protocol OAuth.
 *
 * The client stores every key it owns in this form: the per-session DPoP keys inside
 * [OAuthStoredSession] and [OAuthAuthorizationState], and the client authentication keys in
 * [OAuthClientKey]. Keeping keys as plain JSON means a session saved by one server process can be
 * loaded and used by another process on any platform.
 *
 * A key that includes [d] is a private key. Call [toPublicJwk] before publishing it anywhere.
 *
 * @property kty Key type. Always `EC` for the keys this library creates.
 * @property crv Curve name, for example `P-256`.
 * @property x Base64url-encoded x coordinate of the public point.
 * @property y Base64url-encoded y coordinate of the public point.
 * @property d Base64url-encoded private scalar, present only on private keys.
 * @property kid Key identifier. Required on client authentication keys.
 * @property alg JWS algorithm the key is used with, for example `ES256`.
 * @property use Intended key use. Published client keys use `sig`.
 */
@Serializable
public data class JsonWebKey(
    val kty: String,
    val crv: String,
    val x: String,
    val y: String,
    val d: String? = null,
    val kid: String? = null,
    val alg: String? = null,
    val use: String? = null,
) {
    /**
     * Whether this key carries private key material.
     */
    public val isPrivate: Boolean
        get() = d != null

    /**
     * Returns the public half of this key, keeping [kid], [alg], and [use].
     */
    public fun toPublicJwk(): JsonWebKey = copy(d = null)

    /**
     * Returns only the members that identify the public key (`kty`, `crv`, `x`, `y`).
     *
     * DPoP proofs embed this form in their header so the server can compute the key thumbprint.
     */
    public fun toBarePublicJwk(): JsonWebKey = JsonWebKey(kty = kty, crv = crv, x = x, y = y)

    override fun toString(): String = "JsonWebKey(kty=$kty, crv=$crv, kid=$kid, alg=$alg, private=$isPrivate)"
}

/**
 * A JSON Web Key Set (RFC 7517 section 5).
 *
 * @property keys The keys in the set.
 */
@Serializable
public data class JsonWebKeySet(
    val keys: List<JsonWebKey>,
)

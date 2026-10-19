package studio.hypertext.atproto.oauth.client

/**
 * The cryptographic operations the OAuth client needs from its platform.
 *
 * The protocol logic in this module is common Kotlin, but secure randomness, hashing, and
 * elliptic-curve signing come from the platform. On the JVM, use `JvmOAuthCrypto`. Other platforms
 * can supply their own implementation of this interface.
 *
 * Every key crosses this interface as a [JsonWebKey], so keys can be stored as JSON and moved
 * between processes.
 */
public interface OAuthCrypto {
    /**
     * Returns [byteCount] bytes from a cryptographically secure random source.
     */
    public fun randomBytes(byteCount: Int): ByteArray

    /**
     * Returns the SHA-256 digest of [input].
     */
    public fun sha256(input: ByteArray): ByteArray

    /**
     * Generates a new NIST P-256 key pair and returns it as a private [JsonWebKey].
     *
     * The returned key has `kty` `EC`, `crv` `P-256`, and `alg` `ES256`, and no `kid`.
     */
    public fun generateEs256Key(): JsonWebKey

    /**
     * Signs [signingInput] with ES256 (ECDSA over P-256 with SHA-256).
     *
     * @param privateKey A private P-256 key.
     * @return The 64-byte JWS signature: the big-endian `r` value followed by the `s` value.
     */
    public fun signEs256(
        privateKey: JsonWebKey,
        signingInput: ByteArray,
    ): ByteArray
}

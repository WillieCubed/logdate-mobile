package studio.hypertext.atproto.crypto

/**
 * Supported elliptic curves for AT Protocol signing keys and JWKs.
 */
public enum class EcCurve(
    public val signingKeyAlgorithm: String,
    public val jwkCurveName: String,
    public val jwsAlgorithm: String,
    internal val parameterSpecName: String,
    internal val coordinateBytes: Int = 32,
) {
    /**
     * NIST P-256 / secp256r1.
     */
    P256(
        signingKeyAlgorithm = "P-256",
        jwkCurveName = "P-256",
        jwsAlgorithm = "ES256",
        parameterSpecName = "secp256r1",
    ),

    /**
     * secp256k1 / K-256.
     */
    K256(
        signingKeyAlgorithm = "K-256",
        jwkCurveName = "secp256k1",
        jwsAlgorithm = "ES256K",
        parameterSpecName = "secp256k1",
    ),
    ;

    public companion object {
        /**
         * Resolves a stored signing-key [algorithm] value to an [EcCurve].
         */
        public fun fromSigningKeyAlgorithm(algorithm: String): EcCurve? =
            entries.firstOrNull { it.signingKeyAlgorithm.equals(algorithm.trim(), ignoreCase = true) }

        /**
         * Resolves a JWK [crv] value to an [EcCurve].
         */
        public fun fromJwkCurveName(crv: String): EcCurve? =
            entries
                .firstOrNull { it.jwkCurveName.equals(crv.trim(), ignoreCase = true) }
    }
}

package studio.hypertext.atproto.plc

import studio.hypertext.atproto.syntax.Handle

/**
 * Convenience builders for common PLC directory operation payloads.
 */
public object PlcOperations {
    /**
     * Builds an unsigned AT Protocol PLC genesis operation for [handle].
     *
     * The returned operation is ready for signing and DID derivation.
     *
     * @param handle Handle to publish as `alsoKnownAs`.
     * @param pdsServiceEndpoint PDS service endpoint published in the PLC entry.
     * @param signingKeyDidKey DID key used for the `atproto` verification method.
     * @param rotationKeys Rotation keys allowed to sign future PLC updates. Defaults to [signingKeyDidKey].
     */
    public fun atprotoGenesis(
        handle: String,
        pdsServiceEndpoint: String,
        signingKeyDidKey: String,
        rotationKeys: List<String> = listOf(signingKeyDidKey),
    ): PlcUnsignedOperation {
        val normalizedHandle = Handle.require(handle.trim().trim('.').lowercase()).toString()
        val normalizedEndpoint = pdsServiceEndpoint.trim().removeSuffix("/")
        require(normalizedEndpoint.startsWith("https://")) { "PDS service endpoint must use https" }
        require(signingKeyDidKey.startsWith(DID_KEY_PREFIX)) { "signingKeyDidKey must be a did:key" }
        require(rotationKeys.isNotEmpty()) { "rotationKeys must not be empty" }
        require(rotationKeys.all { it.startsWith(DID_KEY_PREFIX) }) { "rotationKeys must all be did:key values" }

        return PlcUnsignedOperation(
            services =
                mapOf(
                    ATPROTO_PDS_SERVICE_ID to
                        PlcService(
                            type = ATPROTO_PDS_SERVICE_TYPE,
                            endpoint = normalizedEndpoint,
                        ),
                ),
            alsoKnownAs = listOf("at://$normalizedHandle"),
            rotationKeys = rotationKeys,
            verificationMethods = mapOf(ATPROTO_VERIFICATION_METHOD_ID to signingKeyDidKey),
        )
    }

    /**
     * Builds an unsigned AT Protocol PLC update operation for [handle].
     *
     * The returned operation preserves the same `alsoKnownAs`, service, and `atproto`
     * verification method shape as [atprotoGenesis], but requires a previous PLC CID.
     */
    public fun atprotoUpdate(
        prevCid: String,
        handle: String,
        pdsServiceEndpoint: String,
        signingKeyDidKey: String,
        rotationKeys: List<String> = listOf(signingKeyDidKey),
    ): PlcUnsignedOperation {
        require(prevCid.isNotBlank()) { "prevCid must not be blank" }
        return atprotoGenesis(
            handle = handle,
            pdsServiceEndpoint = pdsServiceEndpoint,
            signingKeyDidKey = signingKeyDidKey,
            rotationKeys = rotationKeys,
        ).copy(prev = prevCid.trim())
    }

    private const val DID_KEY_PREFIX: String = "did:key:"
    private const val ATPROTO_PDS_SERVICE_ID: String = "atproto_pds"
    private const val ATPROTO_PDS_SERVICE_TYPE: String = "AtprotoPersonalDataServer"
    private const val ATPROTO_VERIFICATION_METHOD_ID: String = "atproto"
}

package studio.hypertext.atproto.identity

import studio.hypertext.atproto.syntax.Handle

/**
 * Resolves an AT Protocol DID into a DID document.
 */
public interface DidResolver {
    /**
     * Resolves [did] to its current DID document.
     */
    public suspend fun resolve(did: AtprotoDid): Result<DidDocument>
}

/**
 * Resolves a handle to its current DID.
 */
public interface HandleResolver {
    /**
     * Resolves [handle] to its current AT Protocol DID.
     */
    public suspend fun resolve(handle: Handle): Result<AtprotoDid>
}

/**
 * Combines handle and DID resolution into a single entrypoint.
 */
public interface IdentityResolver {
    /**
     * Resolves [handle] to its current DID.
     */
    public suspend fun resolveDid(handle: Handle): Result<AtprotoDid>

    /**
     * Resolves [did] to its current DID document.
     */
    public suspend fun resolveDocument(did: AtprotoDid): Result<DidDocument>

    /**
     * Resolves [handle] to a DID and then resolves the DID document.
     */
    public suspend fun resolveDocument(handle: Handle): Result<DidDocument>
}

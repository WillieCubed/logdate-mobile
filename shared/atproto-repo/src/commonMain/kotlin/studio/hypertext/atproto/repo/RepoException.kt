package studio.hypertext.atproto.repo

import studio.hypertext.atproto.identity.AtprotoDid

/**
 * Base failure type for standalone AT Protocol repository abstractions.
 */
public sealed class RepoException(
    message: String,
) : IllegalArgumentException(message)

/**
 * Raised when a collection is outside the supported repo surface.
 */
public class UnsupportedCollectionException(
    public val collection: String,
) : RepoException("Unsupported AT Protocol collection: $collection")

/**
 * Raised when a repo cursor cannot be parsed by the active store implementation.
 */
public class InvalidRepoCursorException(
    public val cursor: String,
) : RepoException("Invalid AT Protocol repo cursor: $cursor")

/**
 * Raised when commits for [repo] cannot be signed or checked because its account or signing key
 * cannot be found. The write that needed the commit fails instead of storing an unsigned commit.
 */
public class RepoSigningKeyUnavailableException(
    public val repo: AtprotoDid,
    public val reason: String,
) : RepoException("Cannot sign or verify commits for $repo: $reason")

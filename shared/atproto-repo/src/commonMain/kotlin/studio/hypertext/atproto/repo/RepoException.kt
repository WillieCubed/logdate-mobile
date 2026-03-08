package studio.hypertext.atproto.repo

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

package app.logdate.core.status

// TODO: Move this to a shared domain-layer module
/**
 * Thrown when a user cannot be found.
 */
class UserNotFoundException(
    /**
     * The UID of the user that could not be found.
     */
    val userId: String
) : Exception(
    "User with ID $userId not found"
)
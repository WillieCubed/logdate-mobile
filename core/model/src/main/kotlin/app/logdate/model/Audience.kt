package app.logdate.model

/**
 * A group of people with the permission to see some collection of content.
 *
 * An audience's membership is one-way and only from the perspective of a given user. In other
 * words,
 *
 * Membership of an audience is static: when an audience is used to determine who has permission to
 * view a given piece of content
 */
sealed class Audience

data class LimitedAudience(
    /**
     * This ID is only guaranteed to be unique for the current device.
     */
    val id: String,
    /**
     * Examples:
     * - Friends
     * - Family
     * - Hiking Buddies
     * - Friends from High School
     * - Besties
     */
    val label: String,
) : Audience()

data class PublicAudience(
    val id: String,
) : Audience()
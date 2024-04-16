package app.logdate.core.installreferrer.model

import kotlinx.datetime.Instant

/**
 * Information about how a user installed the app.
 */
data class InstallReferrerData(
    /**
     * The referrer URL that was used to install the app.
     */
    val referralUrl: String,
    /**
     * True if the user has installed the app from an instant experience within the past seven days.
     */
    val isFromInstantExperience: Boolean,
    /**
     * The timestamp of the click that led to the installation of the app.
     */
    val referrerClickTimestamp: Instant,
    /**
     * The timestamp of the installation of the app.
     */
    val installationTimestamp: Instant,
)
package app.logdate.server.migration

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * How the server moves existing accounts into their personal spaces. Whether it does at all is
 * [app.logdate.server.ServerDescriptorConfig.personalSpaceMigrationEnabled].
 *
 * @param holdGrace how long a hold must have stood before the switch: longer than any request can
 *   take, so a write that passed its storage check just before the hold has finished. Cloud Run ends
 *   a request after 60 seconds on LogDate Cloud.
 * @param holdLifetime how long a hold lasts when the device never finishes, after which writes resume.
 * @param uploadLease how long a device's claim on the move lasts without hearing from it, after which
 *   another device may take the move over.
 * @param legacyRetention how long the legacy copy is kept after the switch; switching back works until then.
 */
data class SpaceMigrationConfig(
    val holdGrace: Duration = 2.minutes,
    val holdLifetime: Duration = 30.minutes,
    val uploadLease: Duration = 1.hours,
    val legacyRetention: Duration = 30.days,
) {
    companion object {
        fun fromEnvironment(readEnv: (String) -> String? = System::getenv): SpaceMigrationConfig {
            val defaults = SpaceMigrationConfig()
            return SpaceMigrationConfig(
                holdGrace =
                    readEnv("LOGDATE_SPACE_MIGRATION_HOLD_GRACE_SECONDS")?.toLongOrNull()?.takeIf { it >= 0 }?.seconds
                        ?: defaults.holdGrace,
            )
        }
    }
}

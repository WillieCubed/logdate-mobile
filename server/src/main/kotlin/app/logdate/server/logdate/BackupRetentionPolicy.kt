package app.logdate.server.logdate

/**
 * Decides which backups to purge for an account.
 *
 * Without retention, backups accumulate indefinitely and both storage cost and list-response size
 * grow unbounded. The policy keeps the most recent [keepPerDevice] backups for each device and
 * anything younger than [maxAgeMillis]; everything else is a candidate for deletion.
 *
 * The [keepPerDevice] rule is the important one — the age rule is a safety net that catches
 * backups from a device the user no longer owns.
 */
data class BackupRetentionPolicy(
    val keepPerDevice: Int,
    val maxAgeMillis: Long,
) {
    /**
     * Given all of an account's backups (in any order), return the subset safe to delete per
     * this policy. Pure: no IO, no clock reads — the caller passes [nowMillis] so tests can pin
     * time.
     */
    fun backupsToPurge(
        backups: List<LogDateBackup>,
        nowMillis: Long,
    ): List<LogDateBackup> {
        if (backups.isEmpty()) return emptyList()
        val oldestAllowed = nowMillis - maxAgeMillis

        // Group by device, sort newest-first, mark the tail beyond keepPerDevice as deletable.
        val excessPerDevice =
            backups
                .groupBy(LogDateBackup::deviceId)
                .flatMap { (_, perDevice) ->
                    perDevice
                        .sortedByDescending(LogDateBackup::createdAt)
                        .drop(keepPerDevice)
                }

        // Anything older than the age cutoff is purged regardless of device history.
        val tooOld = backups.filter { it.createdAt < oldestAllowed }

        return (excessPerDevice + tooOld).distinctBy { it.id }
    }

    companion object {
        /** Sensible default for a launch: 5 most-recent per device + anything younger than 90 days. */
        val DEFAULT: BackupRetentionPolicy =
            BackupRetentionPolicy(
                keepPerDevice = 5,
                maxAgeMillis = 90L * 24 * 60 * 60 * 1000,
            )
    }
}

package app.logdate.server.routes.docs

import app.logdate.server.routes.ErrorCase
import app.logdate.shared.model.sync.DeviceId

/** Shared identifiers and values for the sync examples, consistent with the overview's walkthrough. */
internal object SyncExamples {
    const val CONTENT_ID = "01J7Q2X4Y5Z6A7B8C9D0E1F2G3"
    const val JOURNAL_ID = "01J7Q2Y8N3M4K5L6P7Q8R9S0T1"
    const val DRAFT_ID = "01J7Q30C7D8E9F0G1H2J3K4M5N"
    const val MEDIA_ID = "6f1c2a8e-3b4d-3c5e-9f0a-1b2c3d4e5f60"
    const val BACKUP_ID = "b7e4d2c1-9a8f-4e6d-b5c4-a3f2e1d0c9b8"

    /** The moment the entry was written on the device. */
    const val CREATED_AT = DocExamples.EPOCH_NOW

    /** The version the server assigned when it accepted the write; also the next `since` cursor. */
    const val SERVER_VERSION = 1_789_221_791_530L

    val deviceId = DeviceId(DocExamples.DEVICE_ID)

    const val NOTE_TEXT = "Walked to the lake before work. Fog on the water."

    val serverMisconfigured =
        ErrorCase(
            "SERVER_MISCONFIGURED",
            "This deployment cannot validate tokens (no signing secret configured). No client action helps; the operator must fix the server configuration.",
            "Token service is not configured",
        )
    val invalidSince =
        ErrorCase(
            "INVALID_PARAMETER",
            "`since` is not a whole number. Send the `lastTimestamp` from the previous page, or `0`.",
            "since parameter must be a valid long",
        )
    val conflict =
        ErrorCase(
            "CONFLICT",
            "Your `versionConstraint.serverVersion` is older than what the server holds: another device wrote in between. " +
                "Fetch the current record, merge, and patch again with the new version.",
            "Server has a newer version",
        )
}

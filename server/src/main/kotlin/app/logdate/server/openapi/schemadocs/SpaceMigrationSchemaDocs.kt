package app.logdate.server.openapi.schemadocs

import app.logdate.server.openapi.SchemaDoc

internal object SpaceMigrationSchemaDocs {
    private const val MIGRATION_ID = "The id the moving device chose when it began: up to 64 letters, digits and hyphens."

    val docs =
        mapOf(
            "SpaceMigrationStatus" to
                SchemaDoc(
                    "Where the account's move from legacy storage into its personal space stands. Times are milliseconds since the Unix epoch.",
                    mapOf(
                        "phase" to
                            "One of none, uploading, switched, reverted or cleaned. A value this app doesn't know means a newer server; " +
                            "don't change anything.",
                        "migrationId" to MIGRATION_ID,
                        "startedAt" to "When the device doing the move began it.",
                        "holdReadyAt" to "While writes are held, when every write that began before the hold has finished.",
                        "holdExpiresAt" to "While writes are held, when the hold lapses on its own if the device doesn't finish.",
                        "switchedAt" to "When the account switched into its personal space.",
                        "switchedAtSeq" to "The personal space's last sequence number at the switch.",
                        "legacyKeptUntil" to "When the server deletes the legacy copy. Switching back works until then.",
                        "revertedAt" to "When the account was switched back to legacy storage.",
                        "cleanedAt" to "When the legacy copy was deleted.",
                    ),
                ),
            "SpaceMigrationRequest" to
                SchemaDoc("Names the move a call belongs to.", mapOf("migrationId" to MIGRATION_ID)),
            "LegacyInventory" to
                SchemaDoc(
                    "What legacy storage holds for the account, so a device can compare its own copy before switching.",
                    mapOf(
                        "watermark" to "Changes whenever anything in legacy storage changes. Send it with the switch.",
                        "kinds" to "One entry for each kind legacy storage keeps.",
                    ),
                ),
            "LegacyKindInventory" to
                SchemaDoc(
                    "What legacy storage holds for one kind of data.",
                    mapOf(
                        "kind" to "One of entries, journals, associations, drafts, moments or locationHistory.",
                        "count" to "How many live records of this kind legacy storage holds.",
                        "digest" to "SHA-256, in lowercase hex, of the lowercase ids sorted and each followed by a newline.",
                        "ids" to "The ids themselves, only for the kind the request named in ids.",
                    ),
                ),
            "RegisteredLegacyMedia" to
                SchemaDoc(
                    "A legacy media file the server copied into the personal space as a blob.",
                    mapOf(
                        "mediaId" to "The legacy media id that was copied.",
                        "cid" to "The CID of the blob: CIDv1, raw codec, SHA-256 of the file's encrypted bytes.",
                        "size" to "The blob's size in bytes.",
                    ),
                ),
            "SpaceMigrationSwitchRequest" to
                SchemaDoc(
                    "The device's proof that its check passed.",
                    mapOf(
                        "migrationId" to MIGRATION_ID,
                        "legacyWatermark" to "The legacy inventory watermark the device compared against.",
                        "legacyCounts" to "The live record count of each legacy kind the device compared, by kind.",
                        "spaceSeq" to "The personal space's last sequence number when the device read it back.",
                        "spaceRecordCount" to "How many live records the device read back.",
                        "spaceDigest" to
                            "SHA-256, in lowercase hex, of every live record as <rkey> <cid> lines sorted by rkey, each followed by a newline.",
                    ),
                ),
            "SpaceMigrationSwitchBackRequest" to
                SchemaDoc(
                    "Asks to switch the account back to legacy storage while the legacy copy is kept.",
                    mapOf(
                        "migrationId" to MIGRATION_ID,
                        "spaceSeq" to
                            "The personal space's last sequence number the device downloaded. Anything written after it refuses the call.",
                    ),
                ),
        )
}

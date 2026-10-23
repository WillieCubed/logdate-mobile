package app.logdate.server.routes.docs

import app.logdate.server.openapi.ApiTags
import app.logdate.server.routes.ErrorCase
import app.logdate.server.routes.bearerOperation
import app.logdate.server.routes.jsonBody
import app.logdate.server.routes.ok
import app.logdate.server.routes.syncError
import app.logdate.shared.model.devices.DeviceLinkErrorCodes
import app.logdate.shared.model.sync.LegacyInventory
import app.logdate.shared.model.sync.LegacyKindInventory
import app.logdate.shared.model.sync.LegacyRecordKinds
import app.logdate.shared.model.sync.RegisteredLegacyMedia
import app.logdate.shared.model.sync.SpaceMigrationErrorCodes
import app.logdate.shared.model.sync.SpaceMigrationPhase
import app.logdate.shared.model.sync.SpaceMigrationRequest
import app.logdate.shared.model.sync.SpaceMigrationStatus
import app.logdate.shared.model.sync.SpaceMigrationSwitchBackRequest
import app.logdate.shared.model.sync.SpaceMigrationSwitchRequest
import io.github.smiley4.ktoropenapi.config.ResponsesConfig
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

/** OpenAPI documentation for `/api/v1/space-migration`. */
internal object SpaceMigrationDocs {
    private const val MIGRATION_ID = "5f0c9a7e-2d41-4b8a-9c3e-7a1f6b2d8e90"
    private const val STARTED_AT = DocExamples.EPOCH_NOW
    private const val DIGEST = "3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855f"

    private val uploading =
        SpaceMigrationStatus(SpaceMigrationPhase.UPLOADING, MIGRATION_ID, startedAt = STARTED_AT)

    private val held =
        uploading.copy(holdReadyAt = STARTED_AT + 120_000, holdExpiresAt = STARTED_AT + 1_800_000)

    private val switched =
        SpaceMigrationStatus(
            SpaceMigrationPhase.SWITCHED,
            MIGRATION_ID,
            startedAt = STARTED_AT,
            switchedAt = STARTED_AT + 600_000,
            switchedAtSeq = 412,
            legacyKeptUntil = STARTED_AT + 600_000 + 2_592_000_000,
        )

    private val scopeDenied =
        ErrorCase(
            DeviceLinkErrorCodes.DEVICE_SCOPE_DENIED,
            "A linked device sent this request. Only a signed-in phone or computer can move the journal.",
            "A linked device cannot do this",
        )

    private val disabled =
        ErrorCase(
            "FEATURE_DISABLED",
            "This server doesn't start new moves, because its descriptor doesn't list `personalSpaceMigrationV1`. The status " +
                "and switching back always work, and holds and the legacy inventory still work for an account that switched, " +
                "so it can switch back while its legacy copy is kept.",
            "Moving journals into personal spaces isn't turned on for this server",
        )

    private val notActive =
        ErrorCase(
            SpaceMigrationErrorCodes.NOT_ACTIVE,
            "No move with this `migrationId` is in the step this call needs, or it changed while the call ran. Read the status again.",
            "No move with this id is in the step this call needs",
        )

    private val wrongMode =
        ErrorCase(
            SpaceMigrationErrorCodes.WRONG_STORAGE_MODE,
            "The account isn't in the storage mode this call needs, such as beginning a move for an account already in its " +
                "personal space. Read the account again from `/auth/me`.",
            "This account's storage is personalSpaceV1",
        )

    private val holdNotReady =
        ErrorCase(
            SpaceMigrationErrorCodes.HOLD_NOT_READY,
            "Writes aren't held, the hold lapsed, or it hasn't stood long enough. Call hold, wait until `holdReadyAt`, check again.",
            "Writes aren't held; hold them and check again",
        )

    private val noIdentity =
        ErrorCase(SpaceMigrationErrorCodes.NO_IDENTITY, "The account has no DID, so it has no personal space.", "This account has no DID")

    private fun ResponsesConfig.callerErrors(vararg conflicts: ErrorCase) {
        syncUnauthorized()
        syncError(HttpStatusCode.Forbidden, scopeDenied)
        syncError(HttpStatusCode.NotFound, disabled)
        if (conflicts.isNotEmpty()) syncError(HttpStatusCode.Conflict, *conflicts)
    }

    private fun ResponsesConfig.badBody() =
        syncError(
            HttpStatusCode.BadRequest,
            ErrorCase("VALIDATION_ERROR", "The body isn't the JSON this call takes.", "Invalid request body"),
        )

    val status: RouteConfig.() -> Unit = {
        bearerOperation(
            "getSpaceMigration",
            ApiTags.SPACE_MIGRATION,
            "Read where the move stands",
            """
            Returns the account's move into its personal space: `none` before one begins, `uploading` while a device
            uploads the journal, `switched` once the account syncs through its personal space, `reverted` after it was
            switched back, and `cleaned` once the legacy copy was deleted. While writes are held, `holdReadyAt` and
            `holdExpiresAt` say when the hold is ready and when it lapses on its own.
            """,
        )
        response {
            ok("The move, or `none`.", switched)
            callerErrors()
        }
    }

    val begin: RouteConfig.() -> Unit = {
        bearerOperation(
            "beginSpaceMigration",
            ApiTags.SPACE_MIGRATION,
            "Claim the move",
            """
            Starts moving the account's journal, or resumes the move this device began. Pick a `migrationId` of up to
            64 letters, digits and hyphens, keep it on the device, and send it with every later call. Legacy sync keeps
            working while the device uploads. Only one device moves an account at a time: another device's claim stands
            until it has been silent for an hour.
            """,
        )
        request { jsonBody(SpaceMigrationRequest(MIGRATION_ID), "The move's id.") }
        response {
            ok("The move, uploading.", uploading)
            badBody()
            callerErrors(
                ErrorCase(
                    SpaceMigrationErrorCodes.HELD_ELSEWHERE,
                    "Another device is moving this journal.",
                    "Another device is moving this journal",
                ),
                wrongMode,
                noIdentity,
            )
        }
    }

    val hold: RouteConfig.() -> Unit = {
        bearerOperation(
            "holdSpaceMigrationWrites",
            ApiTags.SPACE_MIGRATION,
            "Hold writes while the device checks",
            """
            Holds writes to the store the account is leaving, so the device's check sees everything: legacy sync writes
            while the move is `uploading`, personal-space writes while it is `switched` and the device is switching back.
            Held writes answer `503` with `Retry-After`, and apps send them again later. Reads keep working. The hold is
            ready at `holdReadyAt`, once every write that began before it has finished, and lapses at `holdExpiresAt`.
            """,
        )
        request { jsonBody(SpaceMigrationRequest(MIGRATION_ID), "The move's id.") }
        response {
            ok("The move with its hold.", held)
            badBody()
            callerErrors(notActive)
        }
    }

    val release: RouteConfig.() -> Unit = {
        bearerOperation(
            "releaseSpaceMigrationWrites",
            ApiTags.SPACE_MIGRATION,
            "Stop holding writes",
            """
            Ends the hold without switching, as when the device's check found a difference. The account stays where it is.
            """,
        )
        request { jsonBody(SpaceMigrationRequest(MIGRATION_ID), "The move's id.") }
        response {
            ok("The move without a hold.", uploading)
            badBody()
            callerErrors(notActive)
        }
    }

    val legacyInventory: RouteConfig.() -> Unit = {
        bearerOperation(
            "getLegacyInventory",
            ApiTags.SPACE_MIGRATION,
            "Count what legacy storage holds",
            """
            For each kind legacy storage keeps, returns how many live records it holds and the SHA-256, in lowercase hex,
            of their lowercase ids sorted and each followed by a newline. A membership's id is `<journal id>:<entry id>`.
            Compare them with the device's own copy before switching. The `watermark` changes whenever anything in
            legacy storage changes; send it with the switch.
            """,
        )
        request {
            queryParameter<String>("ids") {
                description = "A kind, such as `entries`, whose ids to include, to find out which records differ."
                required = false
            }
        }
        response {
            ok(
                "The inventory.",
                LegacyInventory(
                    "c:1789221791530|l:42:42|m:1789221790000:12",
                    listOf(LegacyKindInventory(LegacyRecordKinds.ENTRIES, 12, DIGEST)),
                ),
            )
            callerErrors()
        }
    }

    val registerMedia: RouteConfig.() -> Unit = {
        bearerOperation(
            "registerLegacyMedia",
            ApiTags.SPACE_MIGRATION,
            "Copy a legacy file into the personal space",
            """
            Copies a media file an app already encrypted into the account's personal space as a blob, so the device
            doesn't upload it again. The server checks that the file belongs to the account, copies it inside storage,
            and confirms the copy holds exactly the same bytes before it becomes a blob named by their CID. Refer to it
            from the record as `defs#media` with that CID and size. A file the server encrypted itself can't be copied:
            download it, encrypt it on the device and upload it with `uploadBlob`.
            """,
        )
        request {
            pathParameter<String>("mediaId") { description = "The legacy media id, as `/api/v1/media` named it." }
            jsonBody(SpaceMigrationRequest(MIGRATION_ID), "The id of a move that is uploading.")
        }
        response {
            ok(
                "The blob.",
                RegisteredLegacyMedia(SyncExamples.MEDIA_ID, "bafkreihdwdcefgh4dqkjv67uzcmw7ojee6xedzdetojuzjevtenxquvyku", 262_217),
            )
            badBody()
            syncUnauthorized()
            syncError(HttpStatusCode.Forbidden, scopeDenied)
            syncError(
                HttpStatusCode.NotFound,
                disabled,
                ErrorCase("MEDIA_NOT_FOUND", "The account has no live file with this id.", "The legacy file isn't stored for this account"),
            )
            syncError(
                HttpStatusCode.Conflict,
                notActive,
                noIdentity,
                ErrorCase(
                    SpaceMigrationErrorCodes.MEDIA_NOT_CLIENT_ENCRYPTED,
                    "The file wasn't encrypted by an app. Download it, encrypt it and upload it as a blob.",
                    "The file wasn't encrypted by an app; upload it again",
                ),
            )
            syncError(
                HttpStatusCode.ServiceUnavailable,
                ErrorCase("SERVER_BUSY", "The server is copying as many files as it can. Wait `Retry-After` seconds.", "Try again"),
            )
        }
    }

    val switch: RouteConfig.() -> Unit = {
        bearerOperation(
            "switchToPersonalSpace",
            ApiTags.SPACE_MIGRATION,
            "Switch the account into its personal space",
            """
            Moves the account's storage mode from `legacy` to `personalSpaceV1` once the device has checked its copy. Send
            the legacy `watermark` and counts it compared, and what it read back from the personal space: the last
            sequence number, the number of live records, and the SHA-256, in lowercase hex, of every live record as
            `<rkey> <cid>` lines sorted by rkey, each followed by a newline. The server recomputes all of it while legacy
            writes are held and switches only if everything still matches. Afterwards, legacy sync for this account answers
            `403 SYNC_FORMAT_UPGRADE_REQUIRED`, and the legacy copy is kept for 30 days.
            """,
        )
        request {
            jsonBody(
                SpaceMigrationSwitchRequest(
                    MIGRATION_ID,
                    "c:1789221791530|l:42:42|m:1789221790000:12",
                    mapOf(LegacyRecordKinds.ENTRIES to 12),
                    412,
                    40,
                    DIGEST,
                ),
                "The device's proof that its check passed.",
            )
        }
        response {
            ok("The move, switched.", switched)
            badBody()
            callerErrors(
                notActive,
                wrongMode,
                holdNotReady,
                noIdentity,
                ErrorCase(SpaceMigrationErrorCodes.LEGACY_CHANGED, "Legacy storage changed after the check. Check again.", "Check again"),
                ErrorCase(
                    SpaceMigrationErrorCodes.SPACE_CHANGED,
                    "The personal space doesn't hold what the device checked.",
                    "Check again",
                ),
            )
        }
    }

    val switchBack: RouteConfig.() -> Unit = {
        bearerOperation(
            "switchBackToLegacy",
            ApiTags.SPACE_MIGRATION,
            "Switch the account back to legacy storage",
            """
            Moves the account's storage mode back to `legacy` while the legacy copy is still kept. First hold writes and
            download everything in the personal space; send the last sequence number downloaded, and the server switches
            only if nothing was written after it. The device then sends the legacy store whatever changed after the switch.
            """,
        )
        request { jsonBody(SpaceMigrationSwitchBackRequest(MIGRATION_ID, 418), "The move's id and the last sequence number downloaded.") }
        response {
            ok("The move, reverted.", switched.copy(phase = SpaceMigrationPhase.REVERTED, revertedAt = STARTED_AT + 900_000))
            badBody()
            callerErrors(
                notActive,
                wrongMode,
                holdNotReady,
                noIdentity,
                ErrorCase(SpaceMigrationErrorCodes.WINDOW_CLOSED, "The legacy copy was deleted 30 days after the switch.", "Too late"),
                ErrorCase(SpaceMigrationErrorCodes.SPACE_CHANGED, "Something was written after the device's download.", "Download again"),
            )
        }
    }
}

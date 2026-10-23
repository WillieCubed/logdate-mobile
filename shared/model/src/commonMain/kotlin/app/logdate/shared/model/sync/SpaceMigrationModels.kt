package app.logdate.shared.model.sync

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Where an account's move from legacy storage into its personal space stands, as the server
 * reports it at `GET /api/v1/space-migration`.
 *
 * On the wire it is the string in [wireValue]; a value this app doesn't know reads as [UNRECOGNIZED].
 */
@Serializable(with = SpaceMigrationPhaseSerializer::class)
enum class SpaceMigrationPhase(
    val wireValue: String,
) {
    /** Nothing has started: the account is in legacy storage. */
    NONE("none"),

    /** A device is uploading the journal into the personal space. Legacy sync keeps working. */
    UPLOADING("uploading"),

    /** The account syncs through its personal space. The legacy copy is kept until [SpaceMigrationStatus.legacyKeptUntil]. */
    SWITCHED("switched"),

    /** The account was switched back to legacy storage while the legacy copy was still kept. */
    REVERTED("reverted"),

    /** The legacy copy was deleted after the account had been switched for 30 days. */
    CLEANED("cleaned"),

    /** A phase added after this app was built. */
    UNRECOGNIZED("unrecognized"),
    ;

    companion object {
        fun fromWireValue(value: String): SpaceMigrationPhase =
            entries.firstOrNull { it != UNRECOGNIZED && it.wireValue == value } ?: UNRECOGNIZED
    }
}

/** Writes [SpaceMigrationPhase.wireValue] and reads any string, mapping unknown ones to [SpaceMigrationPhase.UNRECOGNIZED]. */
object SpaceMigrationPhaseSerializer : KSerializer<SpaceMigrationPhase> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.logdate.shared.model.sync.SpaceMigrationPhase", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: SpaceMigrationPhase,
    ) = encoder.encodeString(value.wireValue)

    override fun deserialize(decoder: Decoder): SpaceMigrationPhase = SpaceMigrationPhase.fromWireValue(decoder.decodeString())
}

/**
 * An account's move into its personal space. Times are milliseconds since the Unix epoch.
 *
 * @property migrationId the id the moving device chose when it began; every later call names it.
 * @property holdReadyAt while legacy writes (before the switch) or space writes (before switching
 *   back) are held, when every write that started before the hold has finished, so the device's
 *   check sees everything.
 * @property holdExpiresAt when the hold lapses on its own if the device doesn't finish.
 * @property switchedAtSeq the personal space's last sequence number when the account switched.
 * @property legacyKeptUntil when the server deletes the legacy copy; switching back works until then.
 */
@Serializable
data class SpaceMigrationStatus(
    val phase: SpaceMigrationPhase,
    val migrationId: String? = null,
    val startedAt: Long? = null,
    val holdReadyAt: Long? = null,
    val holdExpiresAt: Long? = null,
    val switchedAt: Long? = null,
    val switchedAtSeq: Long? = null,
    val legacyKeptUntil: Long? = null,
    val revertedAt: Long? = null,
    val cleanedAt: Long? = null,
)

/** Names the move a call belongs to: the body of begin, hold, release and media registration. */
@Serializable
data class SpaceMigrationRequest(
    val migrationId: String,
)

/**
 * The kinds of data legacy storage keeps, as the legacy inventory names them. Each id is
 * lowercase; a membership is `<journal id>:<entry id>`.
 */
object LegacyRecordKinds {
    const val ENTRIES = "entries"
    const val JOURNALS = "journals"
    const val ASSOCIATIONS = "associations"
    const val DRAFTS = "drafts"
    const val MOMENTS = "moments"
    const val LOCATION_HISTORY = "locationHistory"

    val ALL: List<String> = listOf(ENTRIES, JOURNALS, ASSOCIATIONS, DRAFTS, MOMENTS, LOCATION_HISTORY)
}

/**
 * What legacy storage holds for one kind: how many live records, and a digest of their ids, so a
 * device can compare its own copy without downloading every id.
 *
 * @property digest the lowercase hex SHA-256 of the sorted ids, each followed by a newline
 *   (see [SpaceMigrationDigests]).
 * @property ids the ids themselves, only for the kind a request asked for.
 */
@Serializable
data class LegacyKindInventory(
    val kind: String,
    val count: Int,
    val digest: String,
    val ids: List<String>? = null,
)

/**
 * Everything legacy storage holds for an account, for the device's check before the switch.
 *
 * @property watermark changes whenever anything in legacy storage changes; the switch is refused
 *   unless it is still the same.
 */
@Serializable
data class LegacyInventory(
    val watermark: String,
    val kinds: List<LegacyKindInventory>,
)

/** A legacy media file the server copied into the personal space, named by its content identifier. */
@Serializable
data class RegisteredLegacyMedia(
    val mediaId: String,
    val cid: String,
    val size: Long,
)

/**
 * The device's proof that its check passed, sent to switch the account into its personal space.
 *
 * @property legacyWatermark the [LegacyInventory.watermark] the device compared against.
 * @property legacyCounts the live record count of each legacy kind the device compared.
 * @property spaceSeq the personal space's last sequence number when the device read it back.
 * @property spaceRecordCount how many live records the device read back.
 * @property spaceDigest the lowercase hex SHA-256 of every live record as `<rkey> <cid>` lines,
 *   sorted by rkey (see [SpaceMigrationDigests]).
 */
@Serializable
data class SpaceMigrationSwitchRequest(
    val migrationId: String,
    val legacyWatermark: String,
    val legacyCounts: Map<String, Int>,
    val spaceSeq: Long,
    val spaceRecordCount: Int,
    val spaceDigest: String,
)

/**
 * Switches an account back to legacy storage while the legacy copy is kept.
 *
 * @property spaceSeq the personal space's last sequence number the device downloaded; the server
 *   refuses when anything was written after it.
 */
@Serializable
data class SpaceMigrationSwitchBackRequest(
    val migrationId: String,
    val spaceSeq: Long,
)

/** Error codes the `/api/v1/space-migration` routes answer with. */
object SpaceMigrationErrorCodes {
    /** Another device is moving this account's journal. Wait for it, or try again once it stops. */
    const val HELD_ELSEWHERE = "SPACE_MIGRATION_HELD_ELSEWHERE"

    /** No move with this id is in the phase the call needs; read the status again. */
    const val NOT_ACTIVE = "SPACE_MIGRATION_NOT_ACTIVE"

    /** The account isn't in the storage mode the call needs, such as beginning a move twice. */
    const val WRONG_STORAGE_MODE = "SPACE_MIGRATION_WRONG_STORAGE_MODE"

    /** Writes aren't held, or not for long enough yet; the error says when the hold is ready. */
    const val HOLD_NOT_READY = "SPACE_MIGRATION_HOLD_NOT_READY"

    /** Legacy storage changed after the device's check; check again. */
    const val LEGACY_CHANGED = "SPACE_MIGRATION_LEGACY_CHANGED"

    /** The personal space changed after the device's check; check again. */
    const val SPACE_CHANGED = "SPACE_MIGRATION_SPACE_CHANGED"

    /** The legacy copy is gone, so the account can't be switched back. */
    const val WINDOW_CLOSED = "SPACE_MIGRATION_WINDOW_CLOSED"

    /** The legacy file wasn't encrypted by an app, so it can't be copied as it is; upload it again. */
    const val MEDIA_NOT_CLIENT_ENCRYPTED = "SPACE_MIGRATION_MEDIA_NOT_CLIENT_ENCRYPTED"

    /** The account has no DID, so it has no personal space to move into. */
    const val NO_IDENTITY = "SPACE_MIGRATION_NO_IDENTITY"

    val ALL: List<String> =
        listOf(
            HELD_ELSEWHERE,
            NOT_ACTIVE,
            WRONG_STORAGE_MODE,
            HOLD_NOT_READY,
            LEGACY_CHANGED,
            SPACE_CHANGED,
            WINDOW_CLOSED,
            MEDIA_NOT_CLIENT_ENCRYPTED,
            NO_IDENTITY,
        )
}

/**
 * The text the migration digests hash, so the device and the server hash exactly the same bytes.
 * Each side hashes the UTF-8 of the text with SHA-256 and writes it as lowercase hex.
 */
object SpaceMigrationDigests {
    /** The ids, lowercased and sorted, each followed by a newline. */
    fun idsText(ids: Collection<String>): String = ids.map { it.lowercase() }.sorted().joinToString("") { "$it\n" }

    /** Each live record as `<rkey> <cid>`, sorted by rkey, each followed by a newline. */
    fun spaceText(records: Collection<Pair<String, String>>): String =
        records.sortedBy { it.first }.joinToString("") { (rkey, cid) -> "$rkey $cid\n" }
}

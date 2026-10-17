package app.logdate.client.repository.journals

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * What the person was doing when a moment was captured, while activity awareness was on.
 *
 * [wireName] is what every format stores: the database column, the encrypted sync field, archives and
 * the watch's note messages. It never changes once shipped. A new activity is a new value, and an app
 * version that does not know it reads it as unknown. Sleep is never recorded.
 */
enum class CaptureActivity(
    val wireName: String,
) {
    MOVING("moving"),
    IN_VEHICLE("in_vehicle"),
    ;

    companion object {
        /** The activity named [wireName], or null for null or a name a newer app version wrote. */
        fun fromWireName(wireName: String?): CaptureActivity? = entries.firstOrNull { it.wireName == wireName }
    }
}

/**
 * Writes a [CaptureActivity] as its [CaptureActivity.wireName] and reads a name this version does not
 * know as null, so a note from a newer app version still decodes.
 */
@OptIn(ExperimentalSerializationApi::class)
object LenientCaptureActivitySerializer : KSerializer<CaptureActivity?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.logdate.client.repository.journals.CaptureActivity", PrimitiveKind.STRING).nullable

    override fun serialize(
        encoder: Encoder,
        value: CaptureActivity?,
    ) {
        if (value == null) {
            encoder.encodeNull()
            return
        }
        encoder.encodeString(value.wireName)
    }

    override fun deserialize(decoder: Decoder): CaptureActivity? {
        if (!decoder.decodeNotNullMark()) return decoder.decodeNull()
        return CaptureActivity.fromWireName(decoder.decodeString())
    }
}

/** A copy of this note with [capturedDuring] as its capture activity; everything else is unchanged. */
fun JournalNote.withCapturedDuring(capturedDuring: CaptureActivity?): JournalNote =
    when (this) {
        is JournalNote.Text -> copy(capturedDuring = capturedDuring)
        is JournalNote.Image -> copy(capturedDuring = capturedDuring)
        is JournalNote.Video -> copy(capturedDuring = capturedDuring)
        is JournalNote.Audio -> copy(capturedDuring = capturedDuring)
    }

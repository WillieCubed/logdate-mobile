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
 * The kind of device a moment was captured on.
 *
 * [wireName] is what every format stores: the database column, the encrypted sync field, archives and
 * the watch's note messages. It never changes once shipped. A new kind is a new value, and an app
 * version that does not know it reads it as unknown.
 */
enum class CaptureDevice(
    val wireName: String,
) {
    PHONE("phone"),
    WATCH("watch"),
    ;

    companion object {
        /** The device named [wireName], or null for null or a name a newer app version wrote. */
        fun fromWireName(wireName: String?): CaptureDevice? = entries.firstOrNull { it.wireName == wireName }
    }
}

/**
 * Writes a [CaptureDevice] as its [CaptureDevice.wireName] and reads a name this version does not know
 * as null, so a note from a newer app version still decodes.
 */
@OptIn(ExperimentalSerializationApi::class)
object LenientCaptureDeviceSerializer : KSerializer<CaptureDevice?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.logdate.client.repository.journals.CaptureDevice", PrimitiveKind.STRING).nullable

    override fun serialize(
        encoder: Encoder,
        value: CaptureDevice?,
    ) {
        if (value == null) {
            encoder.encodeNull()
            return
        }
        encoder.encodeString(value.wireName)
    }

    override fun deserialize(decoder: Decoder): CaptureDevice? {
        if (!decoder.decodeNotNullMark()) return decoder.decodeNull()
        return CaptureDevice.fromWireName(decoder.decodeString())
    }
}

/** Whether this note was captured on a watch. */
val JournalNote.isCapturedOnWatch: Boolean
    get() = capturedOn == CaptureDevice.WATCH

/** A copy of this note with [capturedOn] as its capture device; everything else is unchanged. */
fun JournalNote.withCapturedOn(capturedOn: CaptureDevice?): JournalNote =
    when (this) {
        is JournalNote.Text -> copy(capturedOn = capturedOn)
        is JournalNote.Image -> copy(capturedOn = capturedOn)
        is JournalNote.Video -> copy(capturedOn = capturedOn)
        is JournalNote.Audio -> copy(capturedOn = capturedOn)
    }

package app.logdate.client.domain.export.archive

import app.logdate.shared.model.profile.asBirthdayDateInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/** Writes a date while accepting legacy archive timestamps. */
object BirthdayDateSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BirthdayDate", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) {
        encoder.encodeString(value.toLocalDateTime(TimeZone.UTC).date.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        val value = decoder.decodeString()
        return runCatching { LocalDate.parse(value).atStartOfDayIn(TimeZone.UTC) }
            .getOrElse { Instant.parse(value).asBirthdayDateInstant() }
    }
}

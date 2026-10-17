@file:OptIn(ExperimentalSerializationApi::class)

package studio.hypertext.atproto.lexicon.fixtures

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import studio.hypertext.atproto.lexicon.LexiconRecordKey
import studio.hypertext.atproto.lexicon.data.Bytes
import studio.hypertext.atproto.lexicon.data.CidLink
import studio.hypertext.atproto.lexicon.data.LexiconUnions

public object KitchenSinkLexicon {
    public const val ID: String = "com.example.kitchenSink"
}

/** A closed union with a record member and two members both called location. */
@Serializable(with = KitchenSinkPickSerializer::class)
public sealed interface KitchenSinkPick {
    public data class KitchenSinkLocation(
        val value: studio.hypertext.atproto.lexicon.fixtures.KitchenSinkLocation,
    ) : KitchenSinkPick

    public data class DefsLocation(
        val value: studio.hypertext.atproto.lexicon.fixtures.DefsLocation,
    ) : KitchenSinkPick

    public data class Entry(
        val value: studio.hypertext.atproto.lexicon.fixtures.Entry,
    ) : KitchenSinkPick
}

public object KitchenSinkPickSerializer : KSerializer<KitchenSinkPick> {
    override val descriptor: SerialDescriptor = LexiconUnions.descriptor("com.example.kitchenSink#KitchenSinkPick")

    override fun serialize(
        encoder: Encoder,
        value: KitchenSinkPick,
    ) {
        when (value) {
            is KitchenSinkPick.KitchenSinkLocation ->
                LexiconUnions.encodeMember(
                    encoder,
                    "com.example.kitchenSink#location",
                    KitchenSinkLocation.serializer(),
                    value.value,
                )
            is KitchenSinkPick.DefsLocation ->
                LexiconUnions.encodeMember(
                    encoder,
                    "app.logdate.defs#location",
                    DefsLocation.serializer(),
                    value.value,
                )
            is KitchenSinkPick.Entry ->
                LexiconUnions.encodeMember(
                    encoder,
                    "app.logdate.journal.entry",
                    Entry.serializer(),
                    value.value,
                )
        }
    }

    override fun deserialize(decoder: Decoder): KitchenSinkPick {
        val member = LexiconUnions.decodeMember(decoder)
        return when (member.type) {
            "com.example.kitchenSink#location" ->
                KitchenSinkPick.KitchenSinkLocation(member.decode(KitchenSinkLocation.serializer()))
            "app.logdate.defs#location" ->
                KitchenSinkPick.DefsLocation(member.decode(DefsLocation.serializer()))
            "app.logdate.journal.entry" ->
                KitchenSinkPick.Entry(member.decode(Entry.serializer()))
            else -> throw LexiconUnions.notAllowed("com.example.kitchenSink#KitchenSinkPick", member.type)
        }
    }
}

@Serializable
public data class KitchenSink(
    val attachment: CidLink,
    val digest: Bytes? = null,
    val extra: JsonElement? = null,
    /** Required, but may be null. */
    val label: String?,
    /** A property named with a Kotlin keyword. */
    val `object`: String? = null,
    /** A closed union with a record member and two members both called location. */
    val pick: KitchenSinkPick,
    @SerialName("\$type")
    @EncodeDefault
    val lexiconType: String = TYPE,
) {
    public companion object {
        public const val TYPE: String = "com.example.kitchenSink"
        public val KEY: LexiconRecordKey = LexiconRecordKey.Literal("self")
    }
}

@Serializable
public data class KitchenSinkLocation(
    val name: String,
)

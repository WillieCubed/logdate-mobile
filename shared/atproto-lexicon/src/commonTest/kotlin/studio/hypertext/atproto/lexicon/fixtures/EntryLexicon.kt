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
import kotlinx.serialization.json.JsonObject
import studio.hypertext.atproto.lexicon.LexiconRecordKey
import studio.hypertext.atproto.lexicon.data.LexiconUnions

public object EntryLexicon {
    public const val ID: String = "app.logdate.journal.entry"
}

@Serializable(with = EntryBlocksItemSerializer::class)
public sealed interface EntryBlocksItem {
    public data class TextBlock(
        val value: studio.hypertext.atproto.lexicon.fixtures.EntryTextBlock,
    ) : EntryBlocksItem

    public data class ImageBlock(
        val value: studio.hypertext.atproto.lexicon.fixtures.EntryImageBlock,
    ) : EntryBlocksItem

    public data class VideoBlock(
        val value: studio.hypertext.atproto.lexicon.fixtures.EntryVideoBlock,
    ) : EntryBlocksItem

    public data class AudioBlock(
        val value: studio.hypertext.atproto.lexicon.fixtures.EntryAudioBlock,
    ) : EntryBlocksItem

    /** A member this version of the schema doesn't define, kept as it was read. */
    public data class Unknown(
        val type: String,
        val value: JsonObject,
    ) : EntryBlocksItem
}

public object EntryBlocksItemSerializer : KSerializer<EntryBlocksItem> {
    override val descriptor: SerialDescriptor = LexiconUnions.descriptor("app.logdate.journal.entry#EntryBlocksItem")

    override fun serialize(
        encoder: Encoder,
        value: EntryBlocksItem,
    ) {
        when (value) {
            is EntryBlocksItem.TextBlock ->
                LexiconUnions.encodeMember(
                    encoder,
                    "app.logdate.journal.entry#textBlock",
                    EntryTextBlock.serializer(),
                    value.value,
                )
            is EntryBlocksItem.ImageBlock ->
                LexiconUnions.encodeMember(
                    encoder,
                    "app.logdate.journal.entry#imageBlock",
                    EntryImageBlock.serializer(),
                    value.value,
                )
            is EntryBlocksItem.VideoBlock ->
                LexiconUnions.encodeMember(
                    encoder,
                    "app.logdate.journal.entry#videoBlock",
                    EntryVideoBlock.serializer(),
                    value.value,
                )
            is EntryBlocksItem.AudioBlock ->
                LexiconUnions.encodeMember(
                    encoder,
                    "app.logdate.journal.entry#audioBlock",
                    EntryAudioBlock.serializer(),
                    value.value,
                )
            is EntryBlocksItem.Unknown -> LexiconUnions.encodeUnknown(encoder, value.type, value.value)
        }
    }

    override fun deserialize(decoder: Decoder): EntryBlocksItem {
        val member = LexiconUnions.decodeMember(decoder)
        return when (member.type) {
            "app.logdate.journal.entry#textBlock" ->
                EntryBlocksItem.TextBlock(member.decode(EntryTextBlock.serializer()))
            "app.logdate.journal.entry#imageBlock" ->
                EntryBlocksItem.ImageBlock(member.decode(EntryImageBlock.serializer()))
            "app.logdate.journal.entry#videoBlock" ->
                EntryBlocksItem.VideoBlock(member.decode(EntryVideoBlock.serializer()))
            "app.logdate.journal.entry#audioBlock" ->
                EntryBlocksItem.AudioBlock(member.decode(EntryAudioBlock.serializer()))
            else -> EntryBlocksItem.Unknown(member.type, member.value)
        }
    }
}

/** A journal entry: one moment, made of ordered blocks of text, photos, video and audio. */
@Serializable
public data class Entry(
    /** The entry's content, in reading order. */
    val blocks: List<EntryBlocksItem>,
    /** When the moment happened or was first written down. */
    val createdAt: String,
    val location: DefsLocation? = null,
    val updatedAt: String? = null,
    @SerialName("\$type")
    @EncodeDefault
    val lexiconType: String = TYPE,
) {
    public companion object {
        public const val TYPE: String = "app.logdate.journal.entry"
        public val KEY: LexiconRecordKey = LexiconRecordKey.Any
    }
}

/** An audio recording. */
@Serializable
public data class EntryAudioBlock(
    val durationMs: Long,
    /**
     * Identifier of the block, unique within its entry. Transcripts and other records refer to a block by this value.
     */
    val id: String,
    val media: DefsMedia,
    /** The transcript of this recording, when one exists. */
    val transcript: DefsRecordRef? = null,
)

/** A photo. */
@Serializable
public data class EntryImageBlock(
    val caption: String? = null,
    /**
     * Identifier of the block, unique within its entry. Transcripts and other records refer to a block by this value.
     */
    val id: String,
    val media: DefsMedia,
    /** How the photo is framed when the entry is shown. */
    val presentation: String? = null,
)

/** Written text. */
@Serializable
public data class EntryTextBlock(
    /** How to interpret the text. Defaults to markdown. */
    val format: String? = null,
    /**
     * Identifier of the block, unique within its entry. Transcripts and other records refer to a block by this value.
     */
    val id: String,
    val text: String,
)

/** A video. */
@Serializable
public data class EntryVideoBlock(
    val caption: String? = null,
    val durationMs: Long? = null,
    /**
     * Identifier of the block, unique within its entry. Transcripts and other records refer to a block by this value.
     */
    val id: String,
    val media: DefsMedia,
)

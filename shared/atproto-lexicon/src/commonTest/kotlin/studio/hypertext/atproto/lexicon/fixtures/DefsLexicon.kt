package studio.hypertext.atproto.lexicon.fixtures

import kotlinx.serialization.Serializable
import studio.hypertext.atproto.lexicon.data.Blob

public object DefsLexicon {
    public const val ID: String = "app.logdate.defs"
}

/** Width and height of visual media, as a ratio. Used to lay out media before it loads. */
@Serializable
public data class DefsAspectRatio(
    val height: Long,
    val width: Long,
)

/** Where something happened. Coordinates use the community location schema. */
@Serializable
public data class DefsLocation(
    /** Horizontal accuracy of the coordinate, in meters. */
    val accuracyMeters: Long? = null,
    val geo: Geo,
    /** The saved place this location belongs to, when there is one. */
    val place: DefsRecordRef? = null,
    /** IANA time zone where the moment happened, such as America/Chicago. */
    val timeZone: String? = null,
)

/** A photo, video or audio file attached to a record. */
@Serializable
public data class DefsMedia(
    /** Text alternative for people who can't see or hear the media. */
    val alt: String? = null,
    val aspectRatio: DefsAspectRatio? = null,
    /**
     * The stored file. In a personal, circle or group space this is ciphertext, encrypted with the same content key as
     * the record that refers to it.
     */
    val blob: Blob,
    /**
     * The media type of the decrypted file, such as image/jpeg. The blob's own type describes the stored bytes, which
     * may be ciphertext.
     */
    val mimeType: String,
    /** Size of the decrypted file in bytes. */
    val size: Long? = null,
)

/**
 * A reference to another LogDate record in the same space. Records in personal, circle and group spaces aren't publicly
 * addressable, so they point at each other by type and key instead of by AT URI and CID.
 */
@Serializable
public data class DefsRecordRef(
    /** The referenced record's type, such as app.logdate.journal.entry. */
    val collection: String,
    /** The referenced record's key. */
    val rkey: String,
)

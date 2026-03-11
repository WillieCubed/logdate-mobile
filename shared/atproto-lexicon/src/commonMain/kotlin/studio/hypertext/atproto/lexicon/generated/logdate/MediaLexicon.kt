package studio.hypertext.atproto.lexicon.generated.logdate

import kotlinx.serialization.Serializable

public object MediaLexicon {
    public const val ID: String = "studio.hypertext.logdate.media"
}

@Serializable
public data class Media(
    val blobCid: String?,
    val createdAt: Long,
    val deviceId: String?,
    val durationMs: Long?,
    val entryId: String,
    val fileName: String?,
    val mediaId: String,
    val mimeType: String,
    val sizeBytes: Long,
)

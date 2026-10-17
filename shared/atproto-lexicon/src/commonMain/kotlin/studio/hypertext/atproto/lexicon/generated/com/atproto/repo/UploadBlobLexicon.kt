package studio.hypertext.atproto.lexicon.generated.com.atproto.repo

import kotlinx.serialization.Serializable
import studio.hypertext.atproto.lexicon.data.Blob

public object UploadBlobLexicon {
    public const val ID: String = "com.atproto.repo.uploadBlob"
}

@Serializable
public data class UploadBlobOutput(
    val blob: Blob,
)

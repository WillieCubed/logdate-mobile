package studio.hypertext.atproto.lexicon.generated.com.atproto.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object UploadBlobLexicon {
    public const val ID: String = "com.atproto.repo.uploadBlob"
}

@Serializable
public data class UploadBlobOutput(
    val blob: JsonElement,
)

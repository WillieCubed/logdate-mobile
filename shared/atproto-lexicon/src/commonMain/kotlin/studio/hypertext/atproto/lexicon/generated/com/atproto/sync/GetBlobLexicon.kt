package studio.hypertext.atproto.lexicon.generated.com.atproto.sync

import kotlinx.serialization.Serializable

public object GetBlobLexicon {
    public const val ID: String = "com.atproto.sync.getBlob"
}

@Serializable
public data class GetBlobParams(
    val cid: String,
    val did: String,
)

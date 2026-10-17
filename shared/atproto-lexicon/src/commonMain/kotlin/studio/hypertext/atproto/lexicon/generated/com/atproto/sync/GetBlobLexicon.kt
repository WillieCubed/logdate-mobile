package studio.hypertext.atproto.lexicon.generated.com.atproto.sync

import kotlinx.serialization.Serializable

public object GetBlobLexicon {
    public const val ID: String = "com.atproto.sync.getBlob"
}

@Serializable
public data class GetBlobParams(
    /** The CID of the blob to fetch */
    val cid: String,
    /** The DID of the account. */
    val did: String,
)

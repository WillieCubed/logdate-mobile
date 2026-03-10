package studio.hypertext.atproto.lexicon.generated.com.atproto.repo

import kotlinx.serialization.Serializable

public object DefsLexicon {
    public const val ID: String = "com.atproto.repo.defs"
}

@Serializable
public data class DefsCommitMeta(
    val cid: String,
    val rev: String,
)

package studio.hypertext.atproto.lexicon.generated.com.atproto.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object DescribeRepoLexicon {
    public const val ID: String = "com.atproto.repo.describeRepo"
}

@Serializable
public data class DescribeRepoParams(
    val repo: String,
)

@Serializable
public data class DescribeRepoOutput(
    val collections: List<String>,
    val did: String,
    val didDoc: JsonElement,
    val handle: String,
    val handleIsCorrect: Boolean,
)

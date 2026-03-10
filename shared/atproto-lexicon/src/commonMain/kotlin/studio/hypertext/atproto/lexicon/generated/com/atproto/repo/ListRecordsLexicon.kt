package studio.hypertext.atproto.lexicon.generated.com.atproto.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

public object ListRecordsLexicon {
    public const val ID: String = "com.atproto.repo.listRecords"
}

@Serializable
public data class ListRecordsParams(
    val collection: String,
    val cursor: String?,
    val limit: Long?,
    val repo: String,
    val reverse: Boolean?,
)

@Serializable
public data class ListRecordsOutput(
    val cursor: String?,
    val records: List<ListRecordsRecord>,
)

@Serializable
public data class ListRecordsRecord(
    val cid: String,
    val uri: String,
    val value: JsonElement,
)

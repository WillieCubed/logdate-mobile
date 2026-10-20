package app.logdate.client.data.notes.drafts

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Changes known recovery fields without reserializing unfamiliar restored metadata. */
internal fun patchDraftRecoveryJson(
    raw: JsonElement,
    expected: JsonElement,
    replacement: JsonElement,
): JsonElement {
    if (expected == replacement) return raw
    if (raw is JsonObject && expected is JsonObject && replacement is JsonObject) {
        val fields = raw.toMutableMap()
        for ((key, value) in replacement) {
            val previous = expected[key]
            if (previous == value) continue
            fields[key] =
                if (previous != null && raw[key] != null) {
                    patchDraftRecoveryJson(raw.getValue(key), previous, value)
                } else {
                    value
                }
        }
        return JsonObject(fields)
    }
    if (raw is JsonArray && expected is JsonArray && replacement is JsonArray) {
        require(raw.size == expected.size && expected.size == replacement.size) { "Recovery cannot change draft record order" }
        return JsonArray(
            expected.indices.map { index ->
                val before = expected[index]
                val after = replacement[index]
                if (before is JsonObject && after is JsonObject) {
                    for (key in listOf("id", "uid", "blockId", "type")) {
                        require(before[key] == after[key]) { "Recovery cannot replace draft record identity" }
                    }
                }
                patchDraftRecoveryJson(raw[index], before, after)
            },
        )
    }
    return replacement
}

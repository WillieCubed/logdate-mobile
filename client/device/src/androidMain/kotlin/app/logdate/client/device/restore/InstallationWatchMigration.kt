package app.logdate.client.device.restore

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.util.Base64

/** Legacy trust is adopted once, only with this installation's excluded secure pair key. */
class InstallationWatchMigration(
    private val legacy: suspend () -> Preferences,
    private val pairKey: suspend (String) -> ByteArray?,
    private val canAdoptLegacy: () -> Boolean = { true },
) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[COMPLETE] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        if (!shouldMigrate(currentData)) return currentData
        // Earlier restores may already have separated portable metadata from its excluded key.
        // Keep those bytes intact, but renew the exchange instead of promoting unprovable trust.
        val source = if (canAdoptLegacy()) legacy() else emptyPreferences()
        val records = source[LINKS]?.let(::objectOrNull).orEmpty()
        val retained =
            records.filter { (node, value) ->
                val record = value as? JsonObject
                record != null && trustedRecord(node, record) && pairKey(node)?.size == 32
            }
        return currentData.toMutablePreferences().apply {
            this[LINKS] = JsonObject(retained).toString()
            source.asMap().forEach { (key, value) ->
                if (value is String &&
                    validCounter(key.name, value) &&
                    belongsToRetainedExchange(key.name, retained.mapValues { it.value as JsonObject })
                ) {
                    this[stringPreferencesKey(key.name)] = value
                }
            }
            this[COMPLETE] = true
        }
    }

    override suspend fun cleanUp() = Unit

    private fun validCounter(
        name: String,
        value: String,
    ): Boolean = !name.endsWith(".revision") || value.toLongOrNull()?.let { it >= 0 } == true

    private fun belongsToRetainedExchange(
        name: String,
        records: Map<String, JsonObject>,
    ): Boolean {
        if (!name.startsWith("$WINDOWS.")) return false
        val encoded = name.removePrefix("$WINDOWS.")
        val end = encoded.lastIndexOf('}')
        if (end < 0 || encoded.substring(end + 1) !in setOf("", ".revision", ".publishing")) return false
        val peer = objectOrNull(encoded.substring(0, end + 1)) ?: return false
        if (listOf("linkId", "phoneNode", "watchNode", "mode").any {
                (peer[it] as? JsonPrimitive)?.isString != true
            }
        ) {
            return false
        }
        val phone = peer.text("phoneNode") ?: return false
        val watch = peer.text("watchNode") ?: return false
        if (phone.isBlank() || watch.isBlank() || phone == watch) return false
        val record = records[peer.text("watchNode")] ?: return false
        return listOf("linkId", "mode", "accountId", "serverOrigin").all { peer.text(it) == record.text(it) }
    }

    private fun trustedRecord(
        node: String,
        record: JsonObject,
    ): Boolean {
        if (!validSchema(record)) return false
        if (node.isBlank() || record.text("nodeId") != node || record.text("failure") != null) return false
        if (((record["linkedAt"] as? JsonPrimitive)?.longOrNull ?: -1) < 0) return false
        val link = record.text("linkId") ?: return false
        val decoded = runCatching { Base64.getDecoder().decode(link) }.getOrNull() ?: return false
        if (decoded.size != 32 || Base64.getEncoder().encodeToString(decoded) != link) return false
        return when (record.text("mode")) {
            "PAIR" -> record.text("accountId") == null && record.text("serverOrigin") == null
            "CLOUD" -> !record.text("accountId").isNullOrBlank() && !record.text("serverOrigin").isNullOrBlank()
            else -> false
        }
    }

    private fun validSchema(record: JsonObject): Boolean {
        val strings = listOf("nodeId", "displayName", "mode", "linkId", "accountId", "serverOrigin", "sessionId")
        if (strings.any { key ->
                val value = record[key]
                value != null && value != JsonNull && (value !is JsonPrimitive || !value.isString)
            }
        ) {
            return false
        }
        if (listOf("nodeId", "displayName", "mode", "linkId").any { record.text(it) == null }) return false
        if (listOf("lastAttemptAt", "nextAttemptAt", "seenWatchDocAt").any { record[it] == JsonNull }) return false
        val times = listOf("linkedAt", "confirmedAt", "lastAttemptAt", "nextAttemptAt", "seenWatchDocAt")
        if (times.any { key ->
                val value = record[key]
                value != null && value != JsonNull && (value !is JsonPrimitive || value.isString || value.longOrNull == null)
            }
        ) {
            return false
        }
        val attempts = record["attempts"]
        if (attempts != null && (attempts !is JsonPrimitive || attempts.isString || attempts.intOrNull == null)) return false
        if (listOf("failure", "lastFailure").any { key ->
                val value = record[key]
                value != null && value != JsonNull && !validFailure(value)
            }
        ) {
            return false
        }
        val attention = record["attentionShown"]
        return attention == null || (attention is JsonArray && attention.all(::validFailure))
    }

    private fun validFailure(value: kotlinx.serialization.json.JsonElement): Boolean =
        value is JsonPrimitive && value.isString && value.content in FAILURES

    private fun objectOrNull(text: String): JsonObject? = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        val FAILURES =
            setOf(
                "PHONE_NODE_UNKNOWN",
                "WATCH_UNREACHABLE",
                "WATCH_CANNOT_LINK",
                "OFFER_EXPIRED",
                "ANSWER_UNREADABLE",
                "CONFIRMATION_MISMATCH",
                "WATCH_BOUND_TO_ANOTHER_ACCOUNT",
                "BUNDLE_UNAVAILABLE",
                "SIGNED_OUT",
                "LINK_CODE_UNAVAILABLE",
                "KEYS_UNAVAILABLE",
                "DEVICE_LIMIT_REACHED",
                "WATCH_KEY_CONFLICT",
                "WATCH_INSTALL_FAILED",
            )
        val COMPLETE = booleanPreferencesKey("installation.watch.migrated.v1")
        val LINKS = stringPreferencesKey("phone.watch_links.v1")
        const val WINDOWS = "phone.watch_window.published.v1"
    }
}

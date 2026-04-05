package app.logdate.client.domain.recommendation

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

interface PlaceFamiliarityRepository {
    suspend fun get(placeKey: String): PlaceFamiliarityRecord?

    suspend fun recordVisit(
        placeKey: String,
        displayName: String?,
        visitedAt: Instant = Clock.System.now(),
    )
}

class DefaultPlaceFamiliarityRepository(
    private val keyValueStorage: KeyValueStorage,
) : PlaceFamiliarityRepository {
    override suspend fun get(placeKey: String): PlaceFamiliarityRecord? = loadRecords()[placeKey]

    override suspend fun recordVisit(
        placeKey: String,
        displayName: String?,
        visitedAt: Instant,
    ) {
        val records = loadRecords().toMutableMap()
        val existing = records[placeKey]
        val updated =
            when {
                existing == null ->
                    PlaceFamiliarityRecord(
                        placeKey = placeKey,
                        displayName = displayName,
                        visitCount = 1,
                        lastSeenAt = visitedAt,
                    )
                visitedAt - existing.lastSeenAt < REVISIT_COLLAPSE_WINDOW ->
                    existing.copy(
                        displayName = displayName ?: existing.displayName,
                        lastSeenAt = visitedAt,
                    )
                else ->
                    existing.copy(
                        displayName = displayName ?: existing.displayName,
                        visitCount = existing.visitCount + 1,
                        lastSeenAt = visitedAt,
                    )
            }
        records[placeKey] = updated
        keyValueStorage.putString(KEY_PLACE_FAMILIARITY, json.encodeToString(records.values.sortedBy(PlaceFamiliarityRecord::placeKey)))
    }

    private suspend fun loadRecords(): Map<String, PlaceFamiliarityRecord> {
        val stored = keyValueStorage.getString(KEY_PLACE_FAMILIARITY) ?: return emptyMap()
        return runCatching {
            json
                .decodeFromString<List<PlaceFamiliarityRecord>>(stored)
                .associateBy(PlaceFamiliarityRecord::placeKey)
        }.getOrDefault(emptyMap())
    }

    companion object {
        private const val KEY_PLACE_FAMILIARITY = "ambient_place_familiarity"
        private val json = Json { ignoreUnknownKeys = true }
        private val REVISIT_COLLAPSE_WINDOW = 12.hours
    }
}

@Serializable
data class PlaceFamiliarityRecord(
    val placeKey: String,
    val displayName: String?,
    val visitCount: Int,
    @Serializable(with = InstantAsLongSerializer::class)
    val lastSeenAt: Instant,
)

package app.logdate.client.domain.reminders

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.domain.location.LocationStop
import app.logdate.client.domain.location.LocationStopSnapshot
import app.logdate.client.domain.places.PlaceResolutionResult
import app.logdate.client.domain.places.toDisplayName
import app.logdate.client.domain.places.toPlaceKey
import app.logdate.client.domain.recommendation.PlaceFamiliarityRepository
import app.logdate.shared.model.Location
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Uses the latest reliable stay; repeated reads of one arrival retain its original novelty. */
class NovelPlaceArrivalDetector(
    private val storage: KeyValueStorage,
    private val familiarity: PlaceFamiliarityRepository,
    private val locations: suspend () -> LocationStopSnapshot,
    private val resolve: suspend (Location) -> PlaceResolutionResult,
) {
    private val mutex = Mutex()

    suspend fun read(
        now: Instant,
        audience: String,
    ): Instant? =
        mutex.withLock {
            if (audience.isBlank()) return@withLock null
            val snapshot = locations()
            val stop = snapshot.stops.maxWithOrNull(compareBy<LocationStop> { it.endTime }.thenBy { it.startTime }) ?: return@withLock null
            // A recent fix is required; a historic stay cannot establish that someone is still there.
            if (!stop.hasReliableDuration ||
                stop.duration < ReminderOrchestrator.ARRIVAL_DWELL ||
                stop.startTime > now ||
                stop.endTime > now ||
                now - stop.endTime > 5.minutes
            ) {
                return@withLock null
            }
            val fix = snapshot.latest ?: return@withLock null
            val accuracy = fix.accuracyMeters
            if (!fix.isGenuine ||
                !fix.confidence.isFinite() ||
                fix.confidence <= 0 ||
                fix.timestamp < stop.endTime ||
                fix.timestamp > now ||
                now - fix.timestamp > 5.minutes ||
                (accuracy != null && (!accuracy.isFinite() || accuracy < 0 || accuracy > 100)) ||
                !(fix.location.distanceTo(stop.location) <= 100.0)
            ) {
                return@withLock null
            }
            val place = resolve(stop.location)
            val key = place.toPlaceKey() ?: return@withLock null
            val name = place.toDisplayName() ?: return@withLock null
            val previous = load()
            var arrival =
                previous?.takeIf {
                    it.audience == audience && it.place == key && it.start == stop.startTime.toEpochMilliseconds()
                }
            if (arrival == null) {
                arrival =
                    Arrival(
                        audience = audience,
                        place = key,
                        start = stop.startTime.toEpochMilliseconds(),
                        novel = familiarity.get(key) == null,
                    )
                save(arrival)
            }
            if (!arrival.visitRecorded) {
                familiarity.recordVisit(key, name, stop.endTime)
                arrival = arrival.copy(visitRecorded = true)
                save(arrival)
            }
            if (arrival.novel) stop.startTime else null
        }

    private suspend fun load(): Arrival? {
        val raw = storage.getString(STORAGE_KEY) ?: return null
        val value =
            try {
                Json.decodeFromString<Arrival>(raw)
            } catch (e: Exception) {
                throw IllegalStateException("Unreadable reminder arrival", e)
            }
        check(value.schema == 1 && value.audience.isNotBlank() && value.place.isNotBlank()) { "Unsupported reminder arrival" }
        return value
    }

    private suspend fun save(value: Arrival) = storage.putString(STORAGE_KEY, Json.encodeToString(value))

    @Serializable
    private data class Arrival(
        val schema: Int = 1,
        val audience: String,
        val place: String,
        val start: Long,
        val novel: Boolean,
        val visitRecorded: Boolean = false,
    )

    companion object {
        const val STORAGE_KEY = "moment_reminder_arrival_v1"
    }
}

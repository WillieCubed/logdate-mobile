package app.logdate.wear.v2.mark

import app.logdate.client.database.entities.moments.MarkedMomentEntity
import app.logdate.client.database.entities.moments.MarkedMomentState
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.wear.health.HeartRateSampler
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Marks the current moment on the watch. [mark] saves and queues the mark at once, with its time,
 * time zone and this watch, so a slow sensor can never lose it. [enrich] then adds the place and
 * the heart rate, when either can be read, and saves the mark again; only the app on screen can
 * read them. A place or heart rate that can't be read is not a failure: the mark simply has none.
 */
class MarkMomentUseCase(
    private val store: MarkedMomentStore,
    private val deviceId: () -> String,
    private val place: suspend () -> NoteLocation?,
    private val heartRate: HeartRateSampler,
    private val clock: Clock = Clock.System,
    private val timeZoneId: () -> String = { TimeZone.currentSystemDefault().id },
) {
    suspend fun mark(
        capturedAt: Instant? = null,
        capturedTimeZoneId: String? = null,
        markId: Uuid? = null,
    ): MarkedMomentEntity {
        val now = capturedAt ?: clock.now()
        val mark =
            MarkedMomentEntity(
                id = markId ?: Uuid.random(),
                capturedAt = now,
                timeZoneId = capturedTimeZoneId ?: timeZoneId(),
                state = MarkedMomentState.OPEN,
                originDeviceId = deviceId(),
                lastUpdated = now,
            )
        store.create(mark)
        return mark
    }

    /** Adds what can be read in a few seconds and saves the mark again; returns [mark] unchanged when nothing could. */
    suspend fun enrich(mark: MarkedMomentEntity): MarkedMomentEntity {
        val (location, bpm) =
            coroutineScope {
                val location = async { attempt("place") { withTimeoutOrNull(PLACE_TIMEOUT_MS) { place() } } }
                val bpm = async { attempt("heart rate") { heartRate.sample() } }
                location.await() to bpm.await()
            }
        val found = location?.takeIf { it.effectiveLatitude != null && it.effectiveLongitude != null }
        if (found == null && bpm == null) return mark
        val enriched =
            mark.copy(
                latitude = found?.effectiveLatitude ?: mark.latitude,
                longitude = found?.effectiveLongitude ?: mark.longitude,
                locationAccuracy = if (found == null) mark.locationAccuracy else found.coordinates?.accuracy,
                heartRateBpm = bpm ?: mark.heartRateBpm,
                lastUpdated = clock.now(),
            )
        return store.update(enriched) ?: mark
    }

    private suspend fun <T> attempt(
        what: String,
        read: suspend () -> T?,
    ): T? =
        try {
            read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Couldn't read the $what for a mark", e)
            null
        }

    companion object {
        const val PLACE_TIMEOUT_MS = 3_000L
    }
}

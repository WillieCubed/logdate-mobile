package app.logdate.client.domain.location.history.replay

import app.logdate.client.domain.export.ExportLocationHistoryItem
import app.logdate.client.domain.export.LocationHistoryPayload
import app.logdate.client.domain.location.history.HistoryReconstructionParameters
import app.logdate.client.domain.location.history.ReconstructLocationDay
import app.logdate.shared.model.location.JourneyLeg
import app.logdate.shared.model.location.LocationDayItem
import app.logdate.shared.model.location.LocationObservation
import app.logdate.shared.model.location.PlaceVisit
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Replays an exported `location_history.json` through the day reconstruction, old and current,
 * so changes to it can be judged against real recordings instead of tidy fixtures.
 *
 * Skipped unless `LOCATION_REPLAY_JSON` names the file; run it through `./run location-replay`.
 * Everything it writes stays under `LOCATION_REPLAY_OUT`, which defaults to a `replay` folder next
 * to the input. The input is private location history, so keep both out of the repository.
 */
class LocationReplayTool {
    @Test
    fun replay() {
        val input = System.getenv("LOCATION_REPLAY_JSON")?.takeIf { it.isNotBlank() } ?: return
        val inputFile = File(input)
        val output = File(System.getenv("LOCATION_REPLAY_OUT")?.takeIf { it.isNotBlank() } ?: inputFile.resolveSibling("replay").path)
        val zone = System.getenv("LOCATION_REPLAY_ZONE")?.takeIf { it.isNotBlank() }?.let(TimeZone::of) ?: TimeZone.currentSystemDefault()
        val samples = decode(inputFile)
        val days = replayDays(samples, zone, algorithms())
        output.mkdirs()
        val report = formatReplay(days)
        output.resolve("report.txt").writeText(report)
        days.forEach { day ->
            day.itemsByAlgorithm.forEach { (name, items) ->
                output.resolve("${day.date}-${name.slug()}.geojson").writeText(geoJson(items).toString())
            }
        }
        println(report)
        println("Replay written to ${output.absolutePath}")
    }

    private fun algorithms(): List<ReplayAlgorithm> =
        listOf(
            ReplayAlgorithm("before (v2)") { LegacyReconstructLocationDay()(it) },
            ReplayAlgorithm("current (5 min)") { ReconstructLocationDay()(it) },
            ReplayAlgorithm("3 min stays") { withMinimumStay(3.minutes)(it) },
            ReplayAlgorithm("10 min stays") { withMinimumStay(10.minutes)(it) },
        )

    private fun withMinimumStay(minimumStay: Duration) =
        ReconstructLocationDay(parameters = HistoryReconstructionParameters(minimumStay = minimumStay))

    private fun decode(file: File): List<LocationObservation> =
        Json { ignoreUnknownKeys = true }
            .decodeFromString(LocationHistoryPayload.serializer(), file.readText())
            .locationHistory
            .map { it.toObservation() }

    private fun ExportLocationHistoryItem.toObservation() =
        LocationObservation(
            id = sampleId,
            ownerId = userId,
            deviceId = deviceId,
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            speedMetersPerSecond = speedMetersPerSecond,
            bearingDegrees = bearingDegrees,
            source = captureSource,
            isMock = isMock,
        )

    private fun geoJson(items: List<LocationDayItem>): JsonObject =
        buildJsonObject {
            put("type", "FeatureCollection")
            put("features", JsonArray(items.mapNotNull(::feature)))
        }

    private fun feature(item: LocationDayItem): JsonObject? {
        val geometry =
            when (item) {
                is PlaceVisit -> point(item.longitude, item.latitude)
                is JourneyLeg -> item.route.takeIf { it.size >= 2 }?.let { route -> line(route.map { it.longitude to it.latitude }) }
                else -> null
            } ?: return null
        return buildJsonObject {
            put("type", "Feature")
            put("geometry", geometry)
            put(
                "properties",
                buildJsonObject {
                    put("kind", item::class.simpleName)
                    put("start", item.start.toString())
                    put("end", item.end.toString())
                    put("evidence", item.evidenceIds.size)
                    if (item is PlaceVisit) put("confirmed", item.confirmedStay)
                    if (item is JourneyLeg) put("mode", item.mode.name)
                },
            )
        }
    }

    private fun point(
        longitude: Double,
        latitude: Double,
    ) = buildJsonObject {
        put("type", "Point")
        put("coordinates", JsonArray(listOf(JsonPrimitive(longitude), JsonPrimitive(latitude))))
    }

    private fun line(coordinates: List<Pair<Double, Double>>) =
        buildJsonObject {
            put("type", "LineString")
            put("coordinates", JsonArray(coordinates.map { (lon, lat) -> JsonArray(listOf(JsonPrimitive(lon), JsonPrimitive(lat))) }))
        }

    private fun String.slug() = lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}

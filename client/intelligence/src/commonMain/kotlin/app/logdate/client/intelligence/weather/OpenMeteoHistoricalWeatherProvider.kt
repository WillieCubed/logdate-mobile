package app.logdate.client.intelligence.weather

import app.logdate.shared.model.WeatherCategory
import app.logdate.shared.model.WeatherContext
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Open-Meteo Historical Weather API implementation.
 *
 * The Open-Meteo archive API is free, requires no API key, and covers the entire
 * world from 1940 to the present. We hit the daily endpoint, average a handful of
 * fields across the rewind's date range, and bucket the result into a coarse
 * [WeatherCategory] for the title chip.
 *
 * Failure mode is silent: any exception, non-2xx response, or empty payload returns
 * null and the caller proceeds without weather context. Weather is contextual polish,
 * never load-bearing.
 *
 * @see <a href="https://open-meteo.com/en/docs/historical-weather-api">Open-Meteo Historical Weather API</a>
 */
class OpenMeteoHistoricalWeatherProvider(
    private val httpClient: HttpClient,
) : HistoricalWeatherProvider {
    override suspend fun fetch(
        latitude: Double,
        longitude: Double,
        startInclusive: Instant,
        endInclusive: Instant,
    ): WeatherContext? {
        if (endInclusive < startInclusive) return null
        val zone = TimeZone.UTC
        val startDate = startInclusive.toLocalDateTime(zone).date.toString()
        val endDate = endInclusive.toLocalDateTime(zone).date.toString()
        return try {
            val response: HttpResponse =
                httpClient.get(ARCHIVE_ENDPOINT) {
                    parameter("latitude", latitude)
                    parameter("longitude", longitude)
                    parameter("start_date", startDate)
                    parameter("end_date", endDate)
                    parameter(
                        "daily",
                        "temperature_2m_max,temperature_2m_min,temperature_2m_mean,precipitation_sum,weather_code",
                    )
                    parameter("timezone", "UTC")
                }
            if (!response.status.isSuccess()) {
                Napier.w("Open-Meteo returned ${response.status} for ($latitude,$longitude) $startDate..$endDate")
                return null
            }
            response.body<OpenMeteoResponse>().toWeatherContext()
        } catch (e: Exception) {
            Napier.w("Open-Meteo fetch failed for ($latitude,$longitude) $startDate..$endDate", e)
            null
        }
    }

    @Serializable
    private data class OpenMeteoResponse(
        val daily: OpenMeteoDaily? = null,
    )

    @Serializable
    private data class OpenMeteoDaily(
        @SerialName("temperature_2m_max") val tempMax: List<Double?> = emptyList(),
        @SerialName("temperature_2m_min") val tempMin: List<Double?> = emptyList(),
        @SerialName("temperature_2m_mean") val tempMean: List<Double?> = emptyList(),
        @SerialName("precipitation_sum") val precipitation: List<Double?> = emptyList(),
        @SerialName("weather_code") val weatherCodes: List<Int?> = emptyList(),
    )

    private fun OpenMeteoResponse.toWeatherContext(): WeatherContext? {
        val daily = daily ?: return null
        val maxValues = daily.tempMax.filterNotNull()
        val minValues = daily.tempMin.filterNotNull()
        val meanValues = daily.tempMean.filterNotNull()
        if (maxValues.isEmpty() || minValues.isEmpty() || meanValues.isEmpty()) return null
        val precipValues = daily.precipitation.filterNotNull()
        val codes = daily.weatherCodes.filterNotNull()
        return WeatherContext(
            category = pickCategory(codes, precipValues),
            avgTempCelsius = meanValues.average(),
            maxTempCelsius = maxValues.max(),
            minTempCelsius = minValues.min(),
            precipitationMm = precipValues.sum(),
        )
    }

    /**
     * Maps a sequence of WMO weather codes plus daily precipitation totals to one
     * dominant [WeatherCategory] for the period.
     *
     * Heuristic: if any single day saw >5mm of precipitation, the period is rainy or
     * snowy depending on whether the corresponding weather code falls in the snow
     * band. Otherwise the most common code's category wins, with a fallback to
     * [WeatherCategory.MIXED] when there's no clear majority.
     *
     * Reference: https://open-meteo.com/en/docs (Weather variable descriptions).
     */
    private fun pickCategory(
        codes: List<Int>,
        precipitation: List<Double>,
    ): WeatherCategory {
        val anyHeavyPrecip = precipitation.any { it >= HEAVY_PRECIP_MM }
        val anySnowCode = codes.any { it in SNOW_CODES }
        if (anyHeavyPrecip && anySnowCode) return WeatherCategory.SNOWY
        if (anyHeavyPrecip) return WeatherCategory.RAINY
        if (codes.isEmpty()) return WeatherCategory.MIXED
        val byCategory = codes.groupBy { code -> code.toCategory() }
        val dominant = byCategory.maxByOrNull { (_, group) -> group.size } ?: return WeatherCategory.MIXED
        // If the dominant category is fewer than half the days, the period was genuinely
        // mixed and the chip should say so rather than overclaiming.
        return if (dominant.value.size * 2 >= codes.size) dominant.key else WeatherCategory.MIXED
    }

    private fun Int.toCategory(): WeatherCategory =
        when (this) {
            in SUNNY_CODES -> WeatherCategory.SUNNY
            in CLOUDY_CODES -> WeatherCategory.CLOUDY
            in RAIN_CODES -> WeatherCategory.RAINY
            in SNOW_CODES -> WeatherCategory.SNOWY
            else -> WeatherCategory.MIXED
        }

    private companion object {
        const val ARCHIVE_ENDPOINT = "https://archive-api.open-meteo.com/v1/archive"
        const val HEAVY_PRECIP_MM = 5.0

        // WMO weather code buckets — see Open-Meteo docs.
        val SUNNY_CODES = setOf(0, 1)
        val CLOUDY_CODES = setOf(2, 3, 45, 48)
        val RAIN_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
        val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)
    }
}

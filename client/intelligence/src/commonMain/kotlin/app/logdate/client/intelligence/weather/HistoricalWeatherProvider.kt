package app.logdate.client.intelligence.weather

import app.logdate.shared.model.WeatherContext
import kotlin.time.Instant

/**
 * A geographic point used to anchor a weather lookup against the rewind's primary location.
 *
 * Kept as a tiny named type rather than a Pair so call sites read clearly and the
 * synthesizer's parameter list says what it means.
 */
data class WeatherFetchLocation(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Fetches historical weather summaries for a location and date range.
 *
 * Used by the rewind narrative pipeline to attach atmospheric context to a generated
 * rewind so its title panel can render a small weather chip ("rainy week", "75°"). The
 * provider is intentionally best-effort: callers treat a `null` return as "no weather
 * available" and never let weather failure block narrative synthesis.
 */
interface HistoricalWeatherProvider {
    /**
     * Looks up weather for [latitude]/[longitude] from [startInclusive] through
     * [endInclusive]. Returns null when the request fails or the response carries no
     * usable data — the caller is expected to handle null silently and continue
     * without weather context.
     */
    suspend fun fetch(
        latitude: Double,
        longitude: Double,
        startInclusive: Instant,
        endInclusive: Instant,
    ): WeatherContext?
}

/**
 * Drop-in null provider for tests and platforms where no network call is desired.
 *
 * Returns null for every call. Wired into the synthesizer's default-null constructor
 * arg so test fixtures don't have to stub out networking.
 */
object NoOpHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun fetch(
        latitude: Double,
        longitude: Double,
        startInclusive: Instant,
        endInclusive: Instant,
    ): WeatherContext? = null
}

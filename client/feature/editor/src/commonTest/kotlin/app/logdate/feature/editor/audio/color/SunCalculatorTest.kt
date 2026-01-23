package app.logdate.feature.editor.audio.color

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

class SunCalculatorTest {

    @Test
    fun calculatesReasonableSunriseForNycSummer() {
        val lat = 40.7128 // NYC
        val lng = -74.0060
        val date = LocalDate(2024, 6, 21) // Summer solstice

        val result = SunCalculator.calculate(lat, lng, date)

        // Sunrise should be around 09:25 UTC (05:25 EDT) in summer
        assertTrue(result.sunrise.hour in 8..10, "Expected sunrise hour 8-10, got ${result.sunrise.hour}")
    }

    @Test
    fun sunsetIsAfterSunrise() {
        val lat = 37.7749 // San Francisco
        val lng = -122.4194
        val date = LocalDate(2024, 3, 15)

        val result = SunCalculator.calculate(lat, lng, date)

        assertTrue(
            result.sunset > result.sunrise,
            "Sunset (${result.sunset}) should be after sunrise (${result.sunrise})"
        )
    }

    @Test
    fun solarNoonIsBetweenSunriseAndSunset() {
        val lat = 51.5074 // London
        val lng = -0.1278
        val date = LocalDate(2024, 12, 21) // Winter solstice

        val result = SunCalculator.calculate(lat, lng, date)

        assertTrue(
            result.solarNoon > result.sunrise,
            "Solar noon (${result.solarNoon}) should be after sunrise (${result.sunrise})"
        )
        assertTrue(
            result.solarNoon < result.sunset,
            "Solar noon (${result.solarNoon}) should be before sunset (${result.sunset})"
        )
    }

    @Test
    fun worksForSouthernHemisphere() {
        val lat = -33.8688 // Sydney
        val lng = 151.2093
        val date = LocalDate(2024, 12, 21) // Summer in southern hemisphere

        val result = SunCalculator.calculate(lat, lng, date)

        // Should calculate without error
        assertTrue(result.sunset > result.sunrise)
    }

    @Test
    fun solarNoonNearMidDay() {
        val lat = 0.0 // Equator
        val lng = 0.0 // Prime meridian
        val date = LocalDate(2024, 3, 20) // Equinox

        val result = SunCalculator.calculate(lat, lng, date)

        // Solar noon should be around 12:00 UTC at 0 longitude
        assertTrue(
            result.solarNoon.hour in 11..13,
            "Solar noon at equator/prime meridian should be near 12:00, got ${result.solarNoon}"
        )
    }
}

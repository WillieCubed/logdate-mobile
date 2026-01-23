package app.logdate.feature.editor.audio.color

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise, sunset, and solar noon times for a given date and location.
 */
data class SunTimes(
    val sunrise: LocalTime,
    val sunset: LocalTime,
    val solarNoon: LocalTime
)

/**
 * Calculator for sunrise, sunset, and solar noon times.
 *
 * Uses the NOAA Solar Calculator algorithm for accurate results.
 * Reference: https://www.esrl.noaa.gov/gmd/grad/solcalc/calcdetails.html
 */
object SunCalculator {
    private const val ZENITH = 90.833 // Official zenith for sunrise/sunset

    /**
     * Calculates sunrise, sunset, and solar noon times for a given date and location.
     *
     * @param latitude Latitude in decimal degrees (positive = north, negative = south)
     * @param longitude Longitude in decimal degrees (positive = east, negative = west)
     * @param date The date for which to calculate sun times
     * @return SunTimes containing sunrise, sunset, and solar noon
     */
    fun calculate(latitude: Double, longitude: Double, date: LocalDate): SunTimes {
        val julianDay = calculateJulianDay(date.year, date.monthNumber, date.dayOfMonth)
        val julianCentury = (julianDay - 2451545.0) / 36525.0

        val geomMeanLongSun = (280.46646 + julianCentury * (36000.76983 + 0.0003032 * julianCentury)) % 360
        val geomMeanAnomSun = 357.52911 + julianCentury * (35999.05029 - 0.0001537 * julianCentury)
        val eccentEarthOrbit = 0.016708634 - julianCentury * (0.000042037 + 0.0000001267 * julianCentury)

        val sunEqOfCtr = sin(geomMeanAnomSun.toRadians()) * (1.914602 - julianCentury * (0.004817 + 0.000014 * julianCentury)) +
            sin((2 * geomMeanAnomSun).toRadians()) * (0.019993 - 0.000101 * julianCentury) +
            sin((3 * geomMeanAnomSun).toRadians()) * 0.000289

        val sunTrueLong = geomMeanLongSun + sunEqOfCtr
        val sunAppLong = sunTrueLong - 0.00569 - 0.00478 * sin((125.04 - 1934.136 * julianCentury).toRadians())

        val meanObliqEcliptic = 23 + (26 + ((21.448 - julianCentury * (46.815 + julianCentury * (0.00059 - julianCentury * 0.001813)))) / 60) / 60
        val obliqCorr = meanObliqEcliptic + 0.00256 * cos((125.04 - 1934.136 * julianCentury).toRadians())

        val sunDeclin = asin(sin(obliqCorr.toRadians()) * sin(sunAppLong.toRadians())).toDegrees()

        val varY = tan((obliqCorr / 2).toRadians()).let { it * it }

        val eqOfTime = 4 * (varY * sin(2 * geomMeanLongSun.toRadians()) -
            2 * eccentEarthOrbit * sin(geomMeanAnomSun.toRadians()) +
            4 * eccentEarthOrbit * varY * sin(geomMeanAnomSun.toRadians()) * cos(2 * geomMeanLongSun.toRadians()) -
            0.5 * varY * varY * sin(4 * geomMeanLongSun.toRadians()) -
            1.25 * eccentEarthOrbit * eccentEarthOrbit * sin(2 * geomMeanAnomSun.toRadians())).toDegrees()

        val haSunrise = acos(
            (cos(ZENITH.toRadians()) / (cos(latitude.toRadians()) * cos(sunDeclin.toRadians()))) -
                tan(latitude.toRadians()) * tan(sunDeclin.toRadians())
        ).toDegrees()

        val solarNoonMinutes = 720 - 4 * longitude - eqOfTime
        val sunriseMinutes = solarNoonMinutes - haSunrise * 4
        val sunsetMinutes = solarNoonMinutes + haSunrise * 4

        return SunTimes(
            sunrise = minutesToLocalTime(sunriseMinutes),
            sunset = minutesToLocalTime(sunsetMinutes),
            solarNoon = minutesToLocalTime(solarNoonMinutes)
        )
    }

    private fun calculateJulianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun minutesToLocalTime(minutes: Double): LocalTime {
        val totalMinutes = minutes.toInt().coerceIn(0, 1439)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return LocalTime(hours, mins)
    }

    private fun Double.toRadians() = this * PI / 180
    private fun Double.toDegrees() = this * 180 / PI
}

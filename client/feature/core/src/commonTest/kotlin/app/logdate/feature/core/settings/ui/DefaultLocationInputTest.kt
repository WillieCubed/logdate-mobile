package app.logdate.feature.core.settings.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultLocationInputTest {
    @Test
    fun `valid negative coordinates can be saved`() {
        val result =
            validateDefaultLocationInput(
                latitude = "-33.8688",
                longitude = "-151.2093",
                altitude = "12.5",
            )

        assertTrue(result.canSave)
        assertEquals(-33.8688, result.location?.latitude)
        assertEquals(-151.2093, result.location?.longitude)
        assertEquals(12.5, result.location?.altitudeValue)
    }

    @Test
    fun `invalid latitude disables save and reports latitude error`() {
        val result =
            validateDefaultLocationInput(
                latitude = "91",
                longitude = "10",
                altitude = "",
            )

        assertFalse(result.canSave)
        assertEquals(DefaultLocationInputError.InvalidLatitude, result.latitudeError)
    }

    @Test
    fun `invalid longitude disables save and reports longitude error`() {
        val result =
            validateDefaultLocationInput(
                latitude = "45",
                longitude = "181",
                altitude = "",
            )

        assertFalse(result.canSave)
        assertEquals(DefaultLocationInputError.InvalidLongitude, result.longitudeError)
    }

    @Test
    fun `blank altitude saves as zero meters`() {
        val result =
            validateDefaultLocationInput(
                latitude = "45",
                longitude = "90",
                altitude = "",
            )

        assertTrue(result.canSave)
        assertEquals(0.0, result.location?.altitudeValue)
    }

    @Test
    fun `invalid altitude disables save and reports altitude error`() {
        val result =
            validateDefaultLocationInput(
                latitude = "45",
                longitude = "90",
                altitude = "up",
            )

        assertFalse(result.canSave)
        assertEquals(DefaultLocationInputError.InvalidAltitude, result.altitudeError)
    }
}

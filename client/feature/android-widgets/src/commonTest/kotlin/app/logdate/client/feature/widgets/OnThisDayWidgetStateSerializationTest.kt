package app.logdate.client.feature.widgets

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Tests for the JSON serialization of [OnThisDayWidgetState].
 *
 * This suite verifies that all states of the "On This Day" widget—including loading,
 * new user orientation, no memories available, and active memory display—can be
 * correctly persisted and restored.
 */
class OnThisDayWidgetStateSerializationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun `Loading state round-trips through JSON`() {
        val original: OnThisDayWidgetState = OnThisDayWidgetState.Loading
        val encoded = json.encodeToString(OnThisDayWidgetState.serializer(), original)
        val decoded = json.decodeFromString<OnThisDayWidgetState>(encoded)

        assertIs<OnThisDayWidgetState.Loading>(decoded)
    }

    @Test
    fun `NewUser state round-trips through JSON`() {
        val original: OnThisDayWidgetState = OnThisDayWidgetState.NewUser
        val encoded = json.encodeToString(OnThisDayWidgetState.serializer(), original)
        val decoded = json.decodeFromString<OnThisDayWidgetState>(encoded)

        assertIs<OnThisDayWidgetState.NewUser>(decoded)
    }

    @Test
    fun `NoMemoryToday state round-trips through JSON`() {
        val original: OnThisDayWidgetState = OnThisDayWidgetState.NoMemoryToday
        val encoded = json.encodeToString(OnThisDayWidgetState.serializer(), original)
        val decoded = json.decodeFromString<OnThisDayWidgetState>(encoded)

        assertIs<OnThisDayWidgetState.NoMemoryToday>(decoded)
    }

    @Test
    fun `HasMemory state round-trips through JSON`() {
        val original: OnThisDayWidgetState =
            OnThisDayWidgetState.HasMemory(
                dateIso = "2025-03-24",
                dateFormatted = "March 24, 2025",
                summary = "Trip to the park with friends",
                thumbnailUri = "content://media/images/42",
            )

        val encoded = json.encodeToString(OnThisDayWidgetState.serializer(), original)
        val decoded = json.decodeFromString<OnThisDayWidgetState>(encoded)

        assertIs<OnThisDayWidgetState.HasMemory>(decoded)
        assertEquals("2025-03-24", decoded.dateIso)
        assertEquals("March 24, 2025", decoded.dateFormatted)
        assertEquals("Trip to the park with friends", decoded.summary)
        assertEquals("content://media/images/42", decoded.thumbnailUri)
    }

    @Test
    fun `HasMemory with null thumbnail round-trips through JSON`() {
        val original: OnThisDayWidgetState =
            OnThisDayWidgetState.HasMemory(
                dateIso = "2025-01-01",
                dateFormatted = "January 1, 2025",
                summary = "New Year",
                thumbnailUri = null,
            )

        val encoded = json.encodeToString(OnThisDayWidgetState.serializer(), original)
        val decoded = json.decodeFromString<OnThisDayWidgetState>(encoded)

        assertIs<OnThisDayWidgetState.HasMemory>(decoded)
        assertNull(decoded.thumbnailUri)
    }
}

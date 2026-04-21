package app.logdate.client.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.feature.widgets.OnThisDayWidget
import app.logdate.client.feature.widgets.OnThisDayWidgetState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/**
 * Instrumented test verifying the Glance state definition persists and
 * recovers widget state through a real Android file system round-trip.
 *
 * Each test uses a unique fileKey to avoid cross-test DataStore cache pollution.
 */
/**
 * Tests the Glance [GlanceStateDefinition] for the "On This Day" widget.
 *
 * These tests verify that the widget's [OnThisDayWidgetState] is correctly serialized
 * to and from the local [DataStore], ensuring that widget content (like dates and
 * summaries) survives system-initiated process deaths and updates.
 */
@RunWith(AndroidJUnit4::class)
class OnThisDayWidgetStateDefinitionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val widget = OnThisDayWidget()
    private val stateDefinition = widget.stateDefinition
    private val usedKeys = mutableListOf<String>()

    private fun uniqueKey(): String {
        val key = "test-${Uuid.random()}"
        usedKeys.add(key)
        return key
    }

    @After
    fun teardown() {
        usedKeys.forEach { key ->
            stateDefinition.getLocation(context, key).delete()
        }
    }

    @Test
    fun defaultState_isLoading() = runTest {
        val key = uniqueKey()
        val store = stateDefinition.getDataStore(context, key)
        val state = store.data.first()

        assertIs<OnThisDayWidgetState.Loading>(state)
    }

    @Test
    fun writeAndReadHasMemory() = runTest {
        val key = uniqueKey()
        val store = stateDefinition.getDataStore(context, key)
        val expected = OnThisDayWidgetState.HasMemory(
            dateIso = "2025-03-24",
            dateFormatted = "March 24, 2025",
            summary = "Trip to the park with friends",
            thumbnailUri = "content://media/images/42",
        )

        store.updateData { expected }
        val actual = store.data.first()

        assertIs<OnThisDayWidgetState.HasMemory>(actual)
        assertEquals(expected.dateIso, actual.dateIso)
        assertEquals(expected.dateFormatted, actual.dateFormatted)
        assertEquals(expected.summary, actual.summary)
        assertEquals(expected.thumbnailUri, actual.thumbnailUri)
    }

    @Test
    fun writeNoMemoryToday_overwritesPreviousMemory() = runTest {
        val key = uniqueKey()
        val store = stateDefinition.getDataStore(context, key)

        store.updateData {
            OnThisDayWidgetState.HasMemory(
                dateIso = "2025-01-01",
                dateFormatted = "January 1, 2025",
                summary = "New Year",
                thumbnailUri = null,
            )
        }
        store.updateData { OnThisDayWidgetState.NoMemoryToday }
        val actual = store.data.first()

        assertIs<OnThisDayWidgetState.NoMemoryToday>(actual)
    }

    @Test
    fun hasMemoryWithNullThumbnail_roundTrips() = runTest {
        val key = uniqueKey()
        val store = stateDefinition.getDataStore(context, key)
        val expected = OnThisDayWidgetState.HasMemory(
            dateIso = "2025-07-04",
            dateFormatted = "July 4, 2025",
            summary = "Independence Day",
            thumbnailUri = null,
        )

        store.updateData { expected }
        val actual = store.data.first()

        assertIs<OnThisDayWidgetState.HasMemory>(actual)
        assertEquals(null, actual.thumbnailUri)
        assertEquals(expected.summary, actual.summary)
    }

    @Test
    fun cachedDataStore_returnsSameInstance() = runTest {
        val key = uniqueKey()
        val store1 = stateDefinition.getDataStore(context, key)
        val store2 = stateDefinition.getDataStore(context, key)

        assertEquals(store1, store2)
    }
}

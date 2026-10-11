package app.logdate.wear.v2.mark

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.wear.health.HeartRateSampler
import app.logdate.wear.v2.haptics.RecordedHaptics
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackgroundMarkerTest {
    private val featureFlags = mockk<FeatureFlagStore> { coEvery { isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) } returns true }
    private val marks = FakeMarkedMomentDao()
    private val haptics = RecordedHaptics()
    private val marker =
        BackgroundMarker(
            featureFlags = featureFlags,
            markMoment =
                MarkMomentUseCase(
                    store = MarkedMomentStore(marks, FakePeerDeliveryDao(), mockk<SyncMetadataService>(relaxed = true)),
                    deviceId = { "watch-1" },
                    place = { null },
                    heartRate = HeartRateSampler.None,
                    timeZoneId = { "UTC" },
                ),
            haptics = haptics,
        )

    @Test
    fun `a mark from outside the app has only its time and is confirmed by a haptic`() =
        runTest {
            assertTrue(marker.mark())

            val mark = marks.rows.values.single()
            assertNull(mark.latitude)
            assertNull(mark.heartRateBpm)
            assertEquals(listOf("momentMarked"), haptics.played)
        }

    @Test
    fun `nothing is marked while the new watch app is off`() =
        runTest {
            coEvery { featureFlags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) } returns false

            assertFalse(marker.mark())

            assertTrue(marks.rows.isEmpty())
            assertTrue(haptics.played.isEmpty())
        }

    @Test
    fun `a mark that cannot be saved is refused with a haptic`() =
        runTest {
            marks.failure = IllegalStateException("database closed")

            assertFalse(marker.mark())

            assertEquals(listOf("actionRejected"), haptics.played)
        }
}

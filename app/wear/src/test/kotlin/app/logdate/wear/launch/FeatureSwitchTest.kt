package app.logdate.wear.launch

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureSwitchTest {
    private val flag = MutableStateFlow(true)
    private val featureFlags = mockk<FeatureFlagStore> { every { observe(FeatureFlag.WATCH_EXPERIENCE_V2) } returns flag }

    @Test
    fun `the switch is off until the flag has been read`() =
        runTest {
            val switch = FeatureSwitch(featureFlags, FeatureFlag.WATCH_EXPERIENCE_V2)
            assertFalse(switch.isOn)

            switch.start(backgroundScope)
            runCurrent()

            assertTrue(switch.isOn)
        }

    @Test
    fun `the switch follows the flag`() =
        runTest {
            val switch = FeatureSwitch(featureFlags, FeatureFlag.WATCH_EXPERIENCE_V2)
            switch.start(backgroundScope)
            runCurrent()

            flag.value = false
            runCurrent()

            assertFalse(switch.isOn)
        }

    @Test
    fun `a flag that cannot be read leaves the switch off`() =
        runTest {
            every { featureFlags.observe(FeatureFlag.WATCH_EXPERIENCE_V2) } returns flow { throw IllegalStateException("datastore closed") }
            val switch = FeatureSwitch(featureFlags, FeatureFlag.WATCH_EXPERIENCE_V2)

            switch.start(backgroundScope)
            runCurrent()

            assertFalse(switch.isOn)
        }
}

package app.logdate.client.sync.datalayer.v2

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests [WearProtocolGate]: version 2 needs the switch, the other device's capability, and for each
 * surface the feature it uses.
 */
class WearProtocolGateTest {
    private val flag = MutableStateFlow(true)
    private val capability = MutableStateFlow(true)
    private val features = MutableStateFlow(setOf(WearFeatures.RECEIPTS))
    private val gate = WearProtocolGate(flag, capability, features)

    @Test
    fun `version 2 is off while the switch is off`() =
        runTest {
            flag.value = false

            assertFalse(gate.isV2Active().first())
        }

    @Test
    fun `version 2 is off while the other device does not advertise it`() =
        runTest {
            capability.value = false

            assertFalse(gate.isV2Active().first())
        }

    @Test
    fun `version 2 is on with the switch and the capability`() =
        runTest {
            assertTrue(gate.isV2Active().first())
        }

    @Test
    fun `a feature needs the other device to list it`() =
        runTest {
            assertTrue(gate.isFeatureActive(WearFeatures.RECEIPTS).first())
            assertFalse(gate.isFeatureActive(WearFeatures.MOMENTS).first())
        }

    @Test
    fun `no feature is active while version 2 is off`() =
        runTest {
            flag.value = false

            assertFalse(gate.isFeatureActive(WearFeatures.RECEIPTS).first())
        }
}

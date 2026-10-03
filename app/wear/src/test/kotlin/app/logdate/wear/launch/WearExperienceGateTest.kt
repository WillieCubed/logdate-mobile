package app.logdate.wear.launch

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WearExperienceGateTest {
    @Test
    fun `the current watch app opens while the flag is off`() =
        runTest {
            assertEquals(WearExperience.V1, WearExperienceGate(FakeFeatureFlagStore(enabled = false)).resolve())
        }

    @Test
    fun `the new watch app opens while the flag is on`() =
        runTest {
            assertEquals(WearExperience.V2, WearExperienceGate(FakeFeatureFlagStore(enabled = true)).resolve())
        }

    @Test
    fun `only the watch experience flag is read`() =
        runTest {
            val flags = FakeFeatureFlagStore(enabled = true)

            WearExperienceGate(flags).resolve()

            assertEquals(listOf(FeatureFlag.WATCH_EXPERIENCE_V2), flags.reads)
        }

    @Test
    fun `a recreated activity keeps its watch app without reading the flag`() =
        runTest {
            val flags = FakeFeatureFlagStore(enabled = false)

            val experience = WearExperienceGate(flags).resolve(restored = WearExperience.V2)

            assertEquals(WearExperience.V2, experience)
            assertTrue(flags.reads.isEmpty())
        }

    @Test
    fun `a failed flag read opens the current watch app`() =
        runTest {
            val flags = FakeFeatureFlagStore(enabled = true, failure = IOException("The preferences file is unreadable"))

            assertEquals(WearExperience.V1, WearExperienceGate(flags).resolve())
        }

    @Test
    fun `a flag read that never finishes opens the current watch app`() =
        runTest {
            val flags = FakeFeatureFlagStore(enabled = true, hangs = true)

            assertEquals(WearExperience.V1, WearExperienceGate(flags).resolve())
        }

    @Test
    fun `saved names map back to experiences`() {
        assertEquals(WearExperience.V2, WearExperience.fromName("V2"))
        assertEquals(WearExperience.V1, WearExperience.fromName("V1"))
        assertNull(WearExperience.fromName(null))
        assertNull(WearExperience.fromName("V3"))
    }

    private class FakeFeatureFlagStore(
        private val enabled: Boolean,
        private val failure: Exception? = null,
        private val hangs: Boolean = false,
    ) : FeatureFlagStore {
        val reads = mutableListOf<FeatureFlag>()

        override fun observe(flag: FeatureFlag): Flow<Boolean> =
            flow {
                reads += flag
                failure?.let { throw it }
                if (hangs) awaitCancellation()
                emit(enabled)
            }

        override suspend fun setEnabled(
            flag: FeatureFlag,
            enabled: Boolean,
        ) = Unit
    }
}

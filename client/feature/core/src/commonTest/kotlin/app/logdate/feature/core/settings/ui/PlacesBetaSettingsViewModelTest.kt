package app.logdate.feature.core.settings.ui

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PlacesBetaSettingsViewModelTest {
    @BeforeTest fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `Settings reads the saved choice and serializes rapid taps`() =
        runTest {
            val store = Store(true)
            val model = PlacesBetaSettingsViewModel(store)
            assertEquals(PlacesBetaSettingsState.Loading, model.state.value)
            advanceUntilIdle()
            assertEquals(PlacesBetaSettingsState.Ready(true), model.state.value)
            model.setEnabled(false)
            model.setEnabled(true)
            advanceUntilIdle()
            assertEquals(listOf(false), store.writes)
            assertEquals(PlacesBetaSettingsState.Ready(false), model.state.value)
            assertEquals(PlacesBetaSettingsState.Ready(false), PlacesBetaSettingsViewModel(store).also { advanceUntilIdle() }.state.value)
        }

    @Test fun `a failed write retains the choice and can be retried`() =
        runTest {
            val store = Store(true)
            val model = PlacesBetaSettingsViewModel(store)
            advanceUntilIdle()
            store.fail = true
            model.setEnabled(false)
            advanceUntilIdle()
            assertEquals(PlacesBetaSettingsState.Ready(true, saveFailed = true), model.state.value)
            store.fail = false
            model.setEnabled(false)
            advanceUntilIdle()
            assertEquals(PlacesBetaSettingsState.Ready(false), model.state.value)
        }

    private class Store(
        enabled: Boolean,
    ) : FeatureFlagStore {
        val value = MutableStateFlow(enabled)
        val writes = mutableListOf<Boolean>()
        var fail = false

        override fun observe(flag: FeatureFlag): Flow<Boolean> = value

        override suspend fun setEnabled(
            flag: FeatureFlag,
            enabled: Boolean,
        ) {
            assertEquals(FeatureFlag.PLACES_BETA, flag)
            if (fail) error("disk unavailable")
            writes += enabled
            value.value = enabled
        }
    }
}

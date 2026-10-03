package app.logdate.feature.core.settings.ui.labs

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LabsSettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Labs offers flags that are off by default and allowed to turn on`() {
        val offered = labsFlags()

        assertTrue(FeatureFlag.WATCH_EXPERIENCE_V2 in offered)
        assertFalse(FeatureFlag.LIBRARY in offered, "a launched feature is not offered")
        assertFalse(FeatureFlag.EVENTS in offered, "a deferred feature cannot be turned on")
    }

    @Test
    fun `every flag Labs offers has a title and description`() {
        assertTrue(labsFlags().all { flag -> flag.labsCopy() != null })
    }

    @Test
    fun `a flag without Labs wording is not offered even when it is off by default`() {
        val offered = labsFlags()
        val withoutWording = FeatureFlag.entries.filter { it.labsCopy() == null }

        assertTrue(withoutWording.isNotEmpty(), "the repo has flags that Labs does not describe")
        assertTrue(withoutWording.none { it in offered })
        assertFalse(FeatureFlag.JOURNAL_MERGE in offered, "journal merge stays gated until the server passes acceptance")
        assertFalse(FeatureFlag.QUICK_SETTINGS_TILES in offered, "an unverified feature is not offered")
    }

    @Test
    fun `the page loads each flag with its stored value`() =
        runTest {
            val viewModel = LabsSettingsViewModel(FakeFeatureFlagStore(), flags = listOf(FeatureFlag.WATCH_EXPERIENCE_V2))
            assertEquals(LabsUiState.Loading, viewModel.uiState.value)

            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            assertEquals(
                LabsUiState.Ready(listOf(LabsFlag(FeatureFlag.WATCH_EXPERIENCE_V2, enabled = false))),
                viewModel.uiState.value,
            )
        }

    @Test
    fun `turning a flag on stores it and shows it on`() =
        runTest {
            val store = FakeFeatureFlagStore()
            val viewModel = LabsSettingsViewModel(store, flags = listOf(FeatureFlag.WATCH_EXPERIENCE_V2))
            backgroundScope.launch { viewModel.uiState.collect { } }

            viewModel.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled = true)
            advanceUntilIdle()

            assertEquals(listOf(FeatureFlag.WATCH_EXPERIENCE_V2 to true), store.writes)
            assertEquals(
                LabsUiState.Ready(listOf(LabsFlag(FeatureFlag.WATCH_EXPERIENCE_V2, enabled = true))),
                viewModel.uiState.value,
            )
        }

    @Test
    fun `a flag changed elsewhere shows its new value`() =
        runTest {
            val store = FakeFeatureFlagStore()
            val viewModel = LabsSettingsViewModel(store, flags = listOf(FeatureFlag.WATCH_EXPERIENCE_V2))
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            store.setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled = true)
            advanceUntilIdle()

            assertEquals(
                LabsUiState.Ready(listOf(LabsFlag(FeatureFlag.WATCH_EXPERIENCE_V2, enabled = true))),
                viewModel.uiState.value,
            )
        }

    @Test
    fun `with nothing in testing the page shows an empty list`() =
        runTest {
            val viewModel = LabsSettingsViewModel(FakeFeatureFlagStore(), flags = emptyList())
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            assertEquals(LabsUiState.Ready(emptyList()), viewModel.uiState.value)
        }
}

private class FakeFeatureFlagStore : FeatureFlagStore {
    private val values = MutableStateFlow<Map<FeatureFlag, Boolean>>(emptyMap())
    val writes = mutableListOf<Pair<FeatureFlag, Boolean>>()

    override fun observe(flag: FeatureFlag): Flow<Boolean> = values.map { stored -> stored[flag] ?: flag.defaultEnabled }

    override suspend fun setEnabled(
        flag: FeatureFlag,
        enabled: Boolean,
    ) {
        writes += flag to enabled
        values.update { stored -> stored + (flag to enabled) }
    }
}

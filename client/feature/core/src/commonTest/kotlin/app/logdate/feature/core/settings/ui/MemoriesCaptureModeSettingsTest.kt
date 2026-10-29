package app.logdate.feature.core.settings.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.datastore.EntryStartMode
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesCaptureModeSettingsTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun savedCaptureDefaultIsReadWithoutOverwritingIt() =
        runTest(dispatcher) {
            val store = CaptureModeStore()
            val preferences = LogdatePreferencesDataSource(store)
            preferences.setEntryStartMode(EntryStartMode.CAMERA)
            val writes = store.writes
            val viewModel = viewModel(preferences)
            advanceUntilIdle()
            assertEquals(EntryStartMode.CAMERA, assertIs<DefaultCaptureModeState.Ready>(viewModel.captureModeState.value).mode)
            assertEquals(writes, store.writes, "Opening Settings must not reset a saved preference")
        }

    @Test
    fun selectingEachDefaultSurvivesReopeningAndPreservesOtherPreferences() =
        runTest(dispatcher) {
            val store = CaptureModeStore()
            val unrelated = stringPreferencesKey("unrelated_setting")
            store.updateData { it.toMutablePreferences().apply { this[unrelated] = "retain" } }
            val preferences = LogdatePreferencesDataSource(store)
            val viewModel = viewModel(preferences)
            advanceUntilIdle()
            for (mode in listOf(
                EntryStartMode.CAMERA,
                EntryStartMode.HANDWRITING,
                EntryStartMode.AUDIO,
                EntryStartMode.GALLERY,
                EntryStartMode.TEXT,
            )) {
                viewModel.setDefaultCaptureMode(mode)
                advanceUntilIdle()
                assertEquals(mode, LogdatePreferencesDataSource(store).observeEntryStartMode().first())
                val reopened = viewModel(LogdatePreferencesDataSource(store))
                advanceUntilIdle()
                assertEquals(mode, assertIs<DefaultCaptureModeState.Ready>(reopened.captureModeState.value).mode)
                assertEquals("retain", store.data.first()[unrelated])
            }
        }

    @Test
    fun failedWriteKeepsSavedSelectionAndSuccessfulRetryClearsFailure() =
        runTest(dispatcher) {
            val store = CaptureModeStore()
            val preferences = LogdatePreferencesDataSource(store)
            preferences.setEntryStartMode(EntryStartMode.HANDWRITING)
            val viewModel = viewModel(preferences)
            advanceUntilIdle()
            store.failWrites = true
            viewModel.setDefaultCaptureMode(EntryStartMode.AUDIO)
            advanceUntilIdle()
            val failed = assertIs<DefaultCaptureModeState.Ready>(viewModel.captureModeState.value)
            assertEquals(EntryStartMode.HANDWRITING, failed.mode)
            assertTrue(failed.saveFailed)
            assertEquals(EntryStartMode.HANDWRITING, preferences.observeEntryStartMode().first())
            store.failWrites = false
            viewModel.setDefaultCaptureMode(EntryStartMode.AUDIO)
            advanceUntilIdle()
            assertEquals(DefaultCaptureModeState.Ready(EntryStartMode.AUDIO), viewModel.captureModeState.value)
            assertEquals(EntryStartMode.AUDIO, preferences.observeEntryStartMode().first())
        }

    private fun viewModel(preferences: LogdatePreferencesDataSource) =
        MemoriesSettingsViewModel(
            object : MemoriesSettingsRepository {
                private val settings = MutableStateFlow(MemoriesSettings())

                override fun observeSettings(): Flow<MemoriesSettings> = settings

                override suspend fun getSettings(): MemoriesSettings = settings.value

                override suspend fun updateSettings(settings: MemoriesSettings) {
                    this.settings.value = settings
                }
            },
            object : MemoriesWidgetInstallController {
                override val uiState = MutableStateFlow<MemoriesWidgetInstallUiState>(MemoriesWidgetInstallUiState.Unsupported)

                override suspend fun requestAddToHomeScreen() = Unit
            },
            preferences,
        )
}

private class CaptureModeStore : DataStore<Preferences> {
    private val values = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = values
    var writes = 0
    var failWrites = false

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        check(!failWrites) { "Synthetic preference write failure" }
        return transform(values.value).also {
            values.value = it
            writes++
        }
    }
}

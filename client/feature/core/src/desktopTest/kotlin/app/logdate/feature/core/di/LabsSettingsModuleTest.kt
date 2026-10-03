package app.logdate.feature.core.di

import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.feature.core.settings.ui.labs.FakeFeatureFlagStore
import app.logdate.feature.core.settings.ui.labs.LabsSettingsViewModel
import app.logdate.feature.core.settings.ui.labs.LabsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * The Labs page on desktop, built the way the app builds it. A desktop has no watch, so a switch
 * for the redesigned watch app would do nothing there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LabsSettingsModuleTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the Labs page on desktop does not offer the watch switch`() =
        runTest {
            val koin =
                koinApplication {
                    modules(
                        labsSettingsModule,
                        module { single<FeatureFlagStore> { FakeFeatureFlagStore() } },
                    )
                }.koin
            val viewModel = koin.get<LabsSettingsViewModel>()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            val state = assertIs<LabsUiState.Ready>(viewModel.uiState.value)
            assertFalse(state.flags.any { row -> row.flag == FeatureFlag.WATCH_EXPERIENCE_V2 })
        }
}

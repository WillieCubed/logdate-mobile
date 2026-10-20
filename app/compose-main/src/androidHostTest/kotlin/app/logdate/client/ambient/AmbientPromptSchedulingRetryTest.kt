package app.logdate.client.ambient

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class AmbientPromptSchedulingRetryTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val values = MutableStateFlow(MemoriesSettings(ambientPromptsEnabled = false))
    private var readSettings: suspend () -> MemoriesSettings = { values.value }
    private val repository =
        object : MemoriesSettingsRepository {
            override suspend fun getSettings() = readSettings()

            override fun observeSettings() = values

            override suspend fun updateSettings(settings: MemoriesSettings) {
                values.value = settings
            }
        }
    private val flags =
        object : FeatureFlagStore {
            override fun observe(flag: FeatureFlag) = flowOf(false)

            override suspend fun setEnabled(
                flag: FeatureFlag,
                enabled: Boolean,
            ) = Unit
        }
    private val shared =
        object : WatchPreferencesRepository {
            @Suppress("UNCHECKED_CAST")
            override fun <T> observe(key: SharedPreferenceKey<T>): Flow<T> = flowOf(false as T)

            @Suppress("UNCHECKED_CAST")
            override suspend fun <T> get(key: SharedPreferenceKey<T>): T = false as T

            override suspend fun <T> set(
                key: SharedPreferenceKey<T>,
                value: T,
            ) = Unit
        }
    private lateinit var scheduler: AmbientPromptScheduler

    @Before fun setup() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        scheduler = AmbientPromptScheduler(context, repository, flags, shared)
    }

    @After fun cleanup() {
        WorkManager
            .getInstance(context)
            .cancelAllWork()
            .result
            .get(10, TimeUnit.SECONDS)
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test fun temporarySchedulerFailureRetriesWithoutAnotherPreferenceChange() =
        runTest {
            var attempts = 0
            readSettings = {
                attempts++
                if (attempts == 1) error("Settings temporarily unavailable")
                values.value
            }
            AmbientPromptSchedulingObserver(repository, scheduler, backgroundScope, flags, shared).start()
            runCurrent()
            assertEquals(1, attempts)
            advanceTimeBy(30_001)
            assertEquals(2, attempts)
            values.value = MemoriesSettings(contextualRecommendationsEnabled = false, ambientPromptsEnabled = false)
            runCurrent()
            assertEquals(3, attempts)
        }

    @Test fun cancellationDoesNotRetryTheSchedulingOperation() =
        runTest {
            var attempts = 0
            readSettings = {
                attempts++
                throw CancellationException("Reconciliation cancelled")
            }
            AmbientPromptSchedulingObserver(repository, scheduler, backgroundScope, flags, shared).start()
            advanceTimeBy(120_001)
            assertEquals(1, attempts)
        }
}

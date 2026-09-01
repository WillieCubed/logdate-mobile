package app.logdate.client.media.audio.tagging

import app.logdate.client.media.audio.download.ModelDownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstallTimeAudioTaggingServiceTest {
    @Test
    fun missingInstallTimeFeatureReportsTaggingUnavailable() =
        runTest {
            val service = InstallTimeAudioTaggingService { null }

            assertEquals(AudioTaggingResult.Unavailable, service.tagAudio("recording.m4a").first())
        }

    @Test
    fun installedFeatureForwardsTaggingResults() =
        runTest {
            val service = InstallTimeAudioTaggingService { FakeAudioTaggingService }

            assertTrue(service.isAvailable)
            assertEquals(
                AudioTaggingResult.Success(emptyList(), isFinal = true),
                service.tagAudio("recording.m4a").first(),
            )
        }

    private object FakeAudioTaggingService : AudioTaggingService {
        override val isAvailable: Boolean = true

        override suspend fun warmUp(): Boolean = true

        override fun tagAudio(audioUri: String): Flow<AudioTaggingResult> = flowOf(AudioTaggingResult.Success(emptyList(), true))

        override val modelDownloadStatus: StateFlow<ModelDownloadStatus> =
            MutableStateFlow(ModelDownloadStatus.NotSupported)

        override fun release() = Unit
    }
}

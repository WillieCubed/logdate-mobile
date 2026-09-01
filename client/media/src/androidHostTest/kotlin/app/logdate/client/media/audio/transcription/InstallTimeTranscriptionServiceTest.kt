package app.logdate.client.media.audio.transcription

import app.logdate.client.media.audio.download.ModelDownloadStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstallTimeTranscriptionServiceTest {
    @Test
    fun missingInstallTimeFeatureStillAttemptsLiveTranscriptionAndReportsUnavailable() =
        runTest {
            val service = InstallTimeTranscriptionService(backgroundScope) { null }

            assertTrue(service.supportsLiveTranscription)
            assertEquals(
                TranscriptionStartResult.Failed(TranscriptionFailure.NotAvailable),
                service.startLiveTranscription(),
            )
            assertEquals(
                TranscriptionResult.Error(TranscriptionFailure.NotAvailable),
                withTimeout(1_000) { service.getTranscriptionFlow().first() },
            )
        }

    @Test
    fun installedFeatureForwardsLiveTranscriptionResults() =
        runTest {
            val delegate = FakeTranscriptionService()
            val service = InstallTimeTranscriptionService(backgroundScope) { delegate }

            assertEquals(TranscriptionStartResult.Started, service.startLiveTranscription())
            assertEquals(
                TranscriptionResult.Success(text = "on-device words", isFinal = true),
                service.stopLiveTranscription(),
            )

            assertEquals(
                TranscriptionResult.Success(text = "on-device words", isFinal = true),
                withTimeout(1_000) { service.getTranscriptionFlow().first() },
            )
        }

    private class FakeTranscriptionService : TranscriptionService {
        private val results = MutableSharedFlow<TranscriptionResult>(replay = 1)

        override fun getTranscriptionFlow(): SharedFlow<TranscriptionResult> = results

        override suspend fun startLiveTranscription(): TranscriptionStartResult {
            results.emit(TranscriptionResult.Success(text = "on-device words", isFinal = true))
            return TranscriptionStartResult.Started
        }

        override suspend fun stopLiveTranscription(): TranscriptionResult =
            TranscriptionResult.Success(text = "on-device words", isFinal = true)

        override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult =
            TranscriptionResult.Error(TranscriptionFailure.NotSupported)

        override suspend fun cancelTranscription() = Unit

        override fun getSupportedLanguages(): List<String> = listOf("en-US")

        override fun setLanguage(languageCode: String) = Unit

        override val supportsLiveTranscription: Boolean = true

        override val supportsFileTranscription: Boolean = false

        override suspend fun resetTranscription() = Unit

        override val offlineModelDownloadStatus: StateFlow<ModelDownloadStatus> =
            MutableStateFlow(ModelDownloadStatus.NotSupported)

        override fun release() = Unit
    }
}

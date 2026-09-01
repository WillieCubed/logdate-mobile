package app.logdate.client.media.audio.transcription

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TerminalTranscriptionHandoffTest {
    @Test
    fun `successful stop result is handed directly to durable persistence`() =
        runTest {
            val expected = TranscriptionResult.Success("the final words", isFinal = true)
            val service = StopResultService(expected)
            var handedOff: TranscriptionResult.Success? = null

            val actual =
                stopLiveTranscriptionWithHandoff(service) { result ->
                    handedOff = result
                }

            assertEquals(expected, actual)
            assertEquals(expected, handedOff)
        }

    @Test
    fun `failed stop result is returned without replacing durable text`() =
        runTest {
            val expected = TranscriptionResult.Error(TranscriptionFailure.AudioError)
            val service = StopResultService(expected)
            var handedOff: TranscriptionResult.Success? = null

            val actual =
                stopLiveTranscriptionWithHandoff(service) { result ->
                    handedOff = result
                }

            assertEquals(expected, actual)
            assertNull(handedOff)
        }

    private class StopResultService(
        private val stopResult: TranscriptionResult,
    ) : TranscriptionService {
        private val results = MutableSharedFlow<TranscriptionResult>(replay = 1)

        override fun getTranscriptionFlow(): SharedFlow<TranscriptionResult> = results

        override suspend fun startLiveTranscription(): TranscriptionStartResult = TranscriptionStartResult.Started

        override suspend fun stopLiveTranscription(): TranscriptionResult = stopResult

        override suspend fun transcribeAudioFile(audioUri: String): TranscriptionResult = stopResult

        override suspend fun cancelTranscription() = Unit

        override fun getSupportedLanguages(): List<String> = listOf("en")

        override fun setLanguage(languageCode: String) = Unit

        override val supportsLiveTranscription: Boolean = true

        override val supportsFileTranscription: Boolean = true

        override suspend fun resetTranscription() = Unit

        override fun release() = Unit
    }
}

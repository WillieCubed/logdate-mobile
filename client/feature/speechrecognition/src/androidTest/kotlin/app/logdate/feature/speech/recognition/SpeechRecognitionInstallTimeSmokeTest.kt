package app.logdate.feature.speech.recognition

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.logdate.client.media.audio.SpeechFeatureProvider
import app.logdate.client.media.audio.transcription.TranscriptionFailure
import app.logdate.client.media.audio.transcription.TranscriptionResult
import app.logdate.client.media.audio.transcription.TranscriptionStartResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SpeechRecognitionInstallTimeSmokeTest {
    @get:Rule
    val microphonePermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test
    fun bInstalledFeatureLoadsItsCoreSpeechModels() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val service = loadProvider().createTranscription(ApplicationProvider.getApplicationContext(), scope)

            try {
                // A pristine managed device must extract all install-time model archives
                // before the native recognizers can load. Keep this cold-install allowance
                // separate from the much shorter, typed live-session initialization timeout.
                withTimeout(180_000) { service.warmUp() }
                assertTrue(service.supportsLiveTranscription)
                assertEquals(listOf("en-US"), service.getSupportedLanguages())
            } finally {
                service.release()
                scope.cancel()
            }
            Unit
        }

    @Test
    fun aInstalledFeatureStartsAndStopsLiveMicrophoneTranscription() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val service = loadProvider().createTranscription(ApplicationProvider.getApplicationContext(), scope)

            try {
                assertEquals(TranscriptionStartResult.Started, service.startLiveTranscription())
                delay(1_000)
                val stopped = withTimeout(60_000) { service.stopLiveTranscription() }
                assertTrue(
                    stopped is TranscriptionResult.Success ||
                        stopped == TranscriptionResult.Error(TranscriptionFailure.NoSpeechDetected),
                )
            } finally {
                service.release()
                scope.cancel()
            }
            Unit
        }

    private fun loadProvider(): SpeechFeatureProvider =
        Class
            .forName("app.logdate.feature.speech.recognition.SpeechRecognitionProvider")
            .getDeclaredField("INSTANCE")
            .get(null) as SpeechFeatureProvider
}

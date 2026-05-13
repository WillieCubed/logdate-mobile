package app.logdate.feature.editor.audio

import app.logdate.feature.editor.audio.extraction.AmplitudeExtractor
import app.logdate.feature.editor.audio.storage.WaveformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Locks in the progressive waveform contract — the UI for long voice notes paints
 * an empty waveform first, fills in as decode advances, and the final state is
 * cached so a subsequent open is one-shot.
 */
class AudioContextProcessorTest {
    @Test
    fun processProgressivelyEmitsEmptyThenSnapshotsThenFinal() =
        runTest {
            val extractor =
                FakeProgressiveExtractor(
                    snapshots =
                        listOf(
                            listOf(0.2f),
                            listOf(0.4f, 0.5f),
                            listOf(0.4f, 0.6f, 0.7f),
                        ),
                    final = listOf(0.5f, 0.7f, 0.8f, 0.9f),
                )
            val storage = InMemoryWaveformStorage()
            val processor =
                AudioContextProcessor(
                    amplitudeExtractor = extractor,
                    waveformStorage = storage,
                    coroutineContext = Dispatchers.Unconfined,
                )

            val emissions =
                processor
                    .processProgressively(
                        audioUri = "fake://audio",
                        durationMs = 1000L,
                        createdAt = Clock.System.now(),
                        latitude = null,
                        longitude = null,
                    ).toList()

            // First emission paints immediately with empty amplitudes.
            assertTrue(emissions.first().amplitudes.isEmpty())
            // Final emission has the full waveform.
            assertEquals(listOf(0.5f, 0.7f, 0.8f, 0.9f), emissions.last().amplitudes)
            // Intermediate snapshots arrive in order.
            val amplitudeProgression = emissions.map { it.amplitudes.size }
            assertEquals(amplitudeProgression.sorted(), amplitudeProgression)
            // Final result is cached.
            assertEquals(listOf(0.5f, 0.7f, 0.8f, 0.9f), storage.load("fake://audio"))
        }

    @Test
    fun processProgressivelyShortCircuitsOnCacheHit() =
        runTest {
            val cached = listOf(0.1f, 0.2f, 0.3f)
            val storage = InMemoryWaveformStorage().apply { save("fake://audio", cached) }
            val extractor = FailingExtractor()
            val processor =
                AudioContextProcessor(
                    amplitudeExtractor = extractor,
                    waveformStorage = storage,
                    coroutineContext = Dispatchers.Unconfined,
                )

            val emissions =
                processor
                    .processProgressively(
                        audioUri = "fake://audio",
                        durationMs = 1000L,
                        createdAt = Clock.System.now(),
                        latitude = null,
                        longitude = null,
                    ).toList()

            assertEquals(1, emissions.size)
            assertEquals(cached, emissions.single().amplitudes)
        }
}

private class FakeProgressiveExtractor(
    private val snapshots: List<List<Float>>,
    private val final: List<Float>,
) : AmplitudeExtractor {
    override suspend fun extractAmplitudes(
        uri: String,
        targetSampleCount: Int,
    ): List<Float> = final

    override fun extractAmplitudesProgressively(
        uri: String,
        targetSampleCount: Int,
    ): Flow<List<Float>> =
        flow {
            snapshots.forEach { snapshot ->
                delay(1)
                emit(snapshot)
            }
            emit(final)
        }
}

private class FailingExtractor : AmplitudeExtractor {
    override suspend fun extractAmplitudes(
        uri: String,
        targetSampleCount: Int,
    ): List<Float> = error("extractor should not be called on cache hit")

    override fun extractAmplitudesProgressively(
        uri: String,
        targetSampleCount: Int,
    ): Flow<List<Float>> = error("extractor should not be called on cache hit")
}

private class InMemoryWaveformStorage : WaveformStorage {
    private val data = mutableMapOf<String, List<Float>>()

    override suspend fun save(
        audioUri: String,
        amplitudes: List<Float>,
    ) {
        data[audioUri] = amplitudes
    }

    override suspend fun load(audioUri: String): List<Float>? = data[audioUri]

    override suspend fun exists(audioUri: String): Boolean = audioUri in data

    override suspend fun delete(audioUri: String) {
        data.remove(audioUri)
    }
}

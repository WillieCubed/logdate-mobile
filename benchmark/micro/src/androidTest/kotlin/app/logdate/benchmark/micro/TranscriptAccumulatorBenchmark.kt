package app.logdate.benchmark.micro

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import app.logdate.client.media.audio.transcription.TimedUtterance
import app.logdate.client.media.audio.transcription.TranscriptAccumulator
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptAccumulatorBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun appendAndBuildLongTranscript() {
        val utterance =
            TimedUtterance(
                text = "Captured a benchmark transcript segment",
                startMs = 0L,
                endMs = 3_000L,
            )

        benchmarkRule.measureRepeated {
            val accumulator = TranscriptAccumulator()
            repeat(64) { index ->
                accumulator.addSegment(
                    text = "segment-$index",
                    utterance = utterance,
                )
                accumulator.setPartial("partial-$index")
            }
            accumulator.build()
            accumulator.buildTimedTranscript()
        }
    }
}

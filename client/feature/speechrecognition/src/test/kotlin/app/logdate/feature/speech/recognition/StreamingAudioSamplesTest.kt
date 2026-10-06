package app.logdate.feature.speech.recognition

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamingAudioSamplesTest {
    @Test
    fun `resampling preserves all samples across codec frame boundaries`() {
        val source = FloatArray(997) { (it % 37 - 18) / 18f }
        for (rate in listOf(8_000, 16_000, 44_100, 48_000)) {
            val expected = referenceResample(source, rate)
            for (chunkSize in listOf(1, 7, 256, 1024)) {
                val resampler = MonoPcmResampler(rate)
                val chunks = mutableListOf<FloatArray>()
                for (start in source.indices step chunkSize) {
                    chunks += resampler.append(source.copyOfRange(start, min(start + chunkSize, source.size)))
                }
                chunks += resampler.finish()
                val actual = chunks.flatMap { it.toList() }
                assertEquals(expected.size, actual.size, "rate=$rate, chunk=$chunkSize")
                expected.forEachIndexed { index, sample ->
                    assertEquals(sample, actual[index], 0.00001f, "rate=$rate, index=$index")
                }
            }
        }
    }

    @Test
    fun `overlapping windows preserve timestamps and mark exactly one final window`() {
        for (size in listOf(1, 4, 5, 6, 9, 10, 13)) {
            val windows = AudioSampleWindows(windowSamples = 5, strideSamples = 4, minimumSamples = 2)
            val actual = mutableListOf<AudioSampleWindow>()
            for (i in 0 until size) actual += windows.append(floatArrayOf(i.toFloat()))
            actual += windows.finish()
            val expectedStarts = mutableListOf<Int>()
            var start = 0
            while (start < size && size - start >= 2) {
                expectedStarts += start
                if (start + 5 >= size) break
                start += 4
            }
            assertEquals(expectedStarts.map(Int::toLong), actual.map { it.startSample }, "size=$size")
            assertEquals(if (actual.isEmpty()) 0 else 1, actual.count { it.isFinal })
            if (actual.isNotEmpty()) assertTrue(actual.last().isFinal)
            actual.forEach { window ->
                val startIndex = window.startSample.toInt()
                assertEquals((startIndex until min(startIndex + 5, size)).map(Int::toFloat), window.samples.toList())
            }
        }
    }

    @Test
    fun `long recordings produce bounded windows before the complete recording is decoded`() {
        val resampler = MonoPcmResampler(48_000)
        val windows = AudioSampleWindows(80_000, 64_000, 16_000)
        val frame = FloatArray(4_800) { 0.25f }
        var outputCount = 0L
        var finalCount = 0
        var lastStart = -1L
        var lastEnd = 0L
        fun consume(values: List<AudioSampleWindow>) {
            for (window in values) {
                assertTrue(window.samples.size in 16_000..80_000)
                assertEquals(if (lastStart < 0) 0L else lastStart + 64_000, window.startSample)
                lastStart = window.startSample
                lastEnd = window.startSample + window.samples.size
                outputCount += window.samples.size
                if (window.isFinal) finalCount++
            }
        }
        repeat(18_000) { index ->
            consume(windows.append(resampler.append(frame)))
            if (index == 100) assertTrue(outputCount > 0, "Tagging must start without retaining the entire recording")
        }
        consume(windows.append(resampler.finish()))
        consume(windows.finish())
        assertEquals(28_800_000L, lastEnd)
        assertEquals(1, finalCount)
    }

    private fun referenceResample(source: FloatArray, sourceRate: Int): FloatArray {
        val ratio = sourceRate.toDouble() / 16_000
        return FloatArray((source.size / ratio).toInt()) { i ->
            val position = i * ratio
            val lower = position.toInt()
            val upper = min(lower + 1, source.size - 1)
            val fraction = (position - lower).toFloat()
            source[lower] * (1 - fraction) + source[upper] * fraction
        }
    }
}

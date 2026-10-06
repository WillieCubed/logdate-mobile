package app.logdate.feature.speech.recognition

internal data class AudioSampleWindow(val samples: FloatArray, val startSample: Long, val isFinal: Boolean)

internal class AudioSampleWindows(
    private val windowSamples: Int,
    private val strideSamples: Int,
    private val minimumSamples: Int,
) {
    private val buffer = FloatArray(windowSamples)
    private var size = 0
    private var freshSamples = 0
    private var startSample = 0L
    private var pending: AudioSampleWindow? = null
    private var finished = false

    init {
        require(windowSamples > 0 && strideSamples in 1..windowSamples && minimumSamples in 1..windowSamples)
    }

    fun append(samples: FloatArray): List<AudioSampleWindow> {
        check(!finished)
        val output = mutableListOf<AudioSampleWindow>()
        var offset = 0
        while (offset < samples.size) {
            val count = minOf(windowSamples - size, samples.size - offset)
            samples.copyInto(buffer, size, offset, offset + count)
            size += count
            freshSamples += count
            offset += count
            if (size == windowSamples) {
                pending?.let(output::add)
                pending = AudioSampleWindow(buffer.copyOf(), startSample, false)
                buffer.copyInto(buffer, 0, strideSamples, windowSamples)
                size = windowSamples - strideSamples
                freshSamples = 0
                startSample += strideSamples
            }
        }
        return output
    }

    fun finish(): List<AudioSampleWindow> {
        if (finished) return emptyList()
        finished = true
        val tail =
            if (freshSamples > 0 && size >= minimumSamples) {
                AudioSampleWindow(buffer.copyOf(size), startSample, true)
            } else {
                null
            }
        val output = mutableListOf<AudioSampleWindow>()
        pending?.let { output += it.copy(isFinal = tail == null) }
        tail?.let(output::add)
        pending = null
        return output
    }
}

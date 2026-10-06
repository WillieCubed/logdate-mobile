package app.logdate.feature.speech.recognition

internal class MonoPcmResampler(
    private val sourceRate: Int,
    private val targetRate: Int = 16_000,
) {
    private val ratio = sourceRate.toDouble() / targetRate
    private var inputCount = 0L
    private var nextTarget = 0L
    private var previous = 0f
    private var pending: Float? = null
    private var finished = false

    init {
        require(sourceRate > 0 && targetRate > 0)
    }

    fun append(samples: FloatArray): FloatArray {
        check(!finished)
        if (samples.isEmpty()) return floatArrayOf()
        val desiredCount = (inputCount + samples.size) * targetRate / sourceRate
        val output = FloatArray(((samples.size.toLong() + 2) * targetRate / sourceRate + 2).toInt())
        var size = 0
        pending?.let {
            if (nextTarget - 1 < desiredCount) {
                output[size++] = it
                pending = null
            }
        }
        for (sample in samples) {
            while (nextTarget * ratio <= inputCount) {
                val position = nextTarget * ratio
                val value =
                    if (position == inputCount.toDouble()) {
                        sample
                    } else {
                        val fraction = (position - (inputCount - 1)).toFloat()
                        previous * (1 - fraction) + sample * fraction
                    }
                if (nextTarget < desiredCount) {
                    output[size++] = value
                } else {
                    check(pending == null)
                    pending = value
                }
                nextTarget++
            }
            previous = sample
            inputCount++
        }
        return output.copyOf(size)
    }

    fun finish(): FloatArray {
        if (finished) return floatArrayOf()
        finished = true
        val desiredCount = inputCount * targetRate / sourceRate
        val pendingCount = if (pending != null && nextTarget - 1 < desiredCount) 1 else 0
        val output = FloatArray((desiredCount - nextTarget).coerceAtLeast(0).toInt() + pendingCount)
        if (pendingCount == 1) output[0] = pending!!
        for (index in pendingCount until output.size) output[index] = previous
        pending = null
        return output
    }
}

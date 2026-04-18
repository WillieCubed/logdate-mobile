package app.logdate.wear.haptic

import android.os.VibrationEffect
import android.os.Vibrator
import io.github.aakira.napier.Napier

enum class HapticPreference {
    FULL,
    REDUCED,
    OFF,
}

class WearHapticEngine(
    private val vibrator: Vibrator,
) {
    private var preference: HapticPreference = HapticPreference.FULL

    fun setPreference(pref: HapticPreference) {
        preference = pref
        Napier.d { "Haptic preference set to $pref" }
    }

    fun confirmTap() {
        vibrateIfAllowed(allowReduced = false) {
            VibrationEffect.createOneShot(15, 80)
        }
    }

    fun startRecording() {
        vibrateIfAllowed(allowReduced = true) {
            VibrationEffect.createOneShot(40, 200)
        }
    }

    fun stopRecording() {
        vibrateIfAllowed(allowReduced = true) {
            VibrationEffect.createWaveform(
                longArrayOf(0, 25, 80, 25),
                intArrayOf(0, 180, 0, 180),
                -1,
            )
        }
    }

    fun pause() {
        vibrateIfAllowed(allowReduced = false) {
            VibrationEffect.createOneShot(10, 60)
        }
    }

    fun resume() {
        vibrateIfAllowed(allowReduced = false) {
            VibrationEffect.createWaveform(
                longArrayOf(0, 10, 50, 10),
                intArrayOf(0, 60, 0, 60),
                -1,
            )
        }
    }

    fun rejection() {
        vibrateIfAllowed(allowReduced = true) {
            // Ramp down from amplitude 40 over 100ms, approximated in three steps
            VibrationEffect.createWaveform(
                longArrayOf(0, 33, 33, 34),
                intArrayOf(0, 40, 25, 10),
                -1,
            )
        }
    }

    fun success() {
        vibrateIfAllowed(allowReduced = true) {
            VibrationEffect.createWaveform(
                longArrayOf(0, 30, 60, 15),
                intArrayOf(0, 200, 0, 120),
                -1,
            )
        }
    }

    fun celebration() {
        vibrateIfAllowed(allowReduced = false) {
            VibrationEffect.createWaveform(
                longArrayOf(0, 40, 50, 10, 50, 40),
                intArrayOf(0, 220, 0, 100, 0, 220),
                -1,
            )
        }
    }

    fun scrollTick() {
        vibrateIfAllowed(allowReduced = false) {
            VibrationEffect.createOneShot(5, 30)
        }
    }

    fun transition() {
        vibrateIfAllowed(allowReduced = false) {
            VibrationEffect.createWaveform(
                longArrayOf(0, 8, 100, 8),
                intArrayOf(0, 50, 0, 40),
                -1,
            )
        }
    }

    fun heartbeat() {
        vibrateIfAllowed(allowReduced = false) {
            // Lub-dub: short pulse, brief gap, longer pulse, long gap
            VibrationEffect.createWaveform(
                longArrayOf(0, 20, 100, 30, 300),
                intArrayOf(0, 180, 0, 180, 0),
                -1,
            )
        }
    }

    fun outgoingPulse() {
        vibrateIfAllowed(allowReduced = false) {
            VibrationEffect.createWaveform(
                longArrayOf(0, 10, 50, 30),
                intArrayOf(0, 60, 0, 120),
                -1,
            )
        }
    }

    fun incomingPulse() {
        vibrateIfAllowed(allowReduced = false) {
            VibrationEffect.createWaveform(
                longArrayOf(0, 30, 50, 10),
                intArrayOf(0, 120, 0, 60),
                -1,
            )
        }
    }

    fun cameraShutter() {
        vibrateIfAllowed(allowReduced = false) {
            VibrationEffect.createOneShot(20, 255)
        }
    }

    fun warning() {
        vibrateIfAllowed(allowReduced = true) {
            // Three short pulses: (15ms on, 40ms off) x3
            VibrationEffect.createWaveform(
                longArrayOf(0, 15, 40, 15, 40, 15),
                intArrayOf(0, 150, 0, 150, 0, 150),
                -1,
            )
        }
    }

    private inline fun vibrateIfAllowed(
        allowReduced: Boolean,
        effect: () -> VibrationEffect,
    ) {
        val shouldVibrate =
            when (preference) {
                HapticPreference.FULL -> true
                HapticPreference.REDUCED -> allowReduced
                HapticPreference.OFF -> false
            }
        if (shouldVibrate) {
            vibrator.vibrate(effect())
        }
    }
}

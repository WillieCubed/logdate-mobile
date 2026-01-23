package app.logdate.feature.editor.ui.audio.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService

/**
 * Android implementation of HapticController using VibrationEffect.
 *
 * Uses the modern VibrationEffect.Composition API on Android 11+ for
 * rich haptic primitives, falling back to basic vibration on older devices.
 */
class AndroidHapticController(
    private val context: Context
) : HapticController {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService<Vibrator>()
    }

    private var lastDragHaptic = 0L
    private val dragHapticThrottleMs = 50L

    override fun onDragStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrateWithEffect(VibrationEffect.EFFECT_TICK)
        } else {
            vibrateLegacy(10)
        }
    }

    override fun onDragging() {
        val now = System.currentTimeMillis()
        if (now - lastDragHaptic < dragHapticThrottleMs) return
        lastDragHaptic = now

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            vibrateWithComposition {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.2f)
            }
        }
        // Skip on older devices to avoid excessive vibration
    }

    override fun onCrossSegment() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            vibrateWithComposition {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrateWithEffect(VibrationEffect.EFFECT_TICK)
        } else {
            vibrateLegacy(15)
        }
    }

    override fun onSnapToSegment() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            vibrateWithComposition {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrateWithEffect(VibrationEffect.EFFECT_CLICK)
        } else {
            vibrateLegacy(20)
        }
    }

    override fun onDragEnd() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            vibrateWithComposition {
                addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.3f)
            }
        }
        // No feedback on older devices for drag end
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun vibrateWithEffect(effectId: Int) {
        vibrator?.vibrate(VibrationEffect.createPredefined(effectId))
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun vibrateWithComposition(block: VibrationEffect.Composition.() -> Unit) {
        val composition = VibrationEffect.startComposition().apply(block).compose()
        vibrator?.vibrate(composition)
    }

    @Suppress("DEPRECATION")
    private fun vibrateLegacy(durationMs: Long) {
        vibrator?.vibrate(durationMs)
    }
}

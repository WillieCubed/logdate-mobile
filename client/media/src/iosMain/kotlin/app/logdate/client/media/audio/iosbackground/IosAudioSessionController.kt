@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package app.logdate.client.media.audio.iosbackground

import io.github.aakira.napier.Napier
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothA2DP
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeSpokenAudio
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSError

/**
 * Helper class to manage iOS audio session for background audio recording.
 *
 * Configures the audio session to allow recording in the background.
 */
class IosAudioSessionController {
    private val audioSession = AVAudioSession.sharedInstance()

    /**
     * Sets up the audio session for recording in the background.
     *
     * @return True if the setup was successful
     */
    fun setupAudioSessionForRecording(): Boolean {
        Napier.d("Setting up iOS audio session for recording")

        val categoryOptions =
            AVAudioSessionCategoryOptionDefaultToSpeaker or
                AVAudioSessionCategoryOptionAllowBluetooth or
                AVAudioSessionCategoryOptionAllowBluetoothA2DP

        val categoryError =
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                audioSession.setCategory(
                    AVAudioSessionCategoryPlayAndRecord,
                    withOptions = categoryOptions,
                    error = errorPtr.ptr,
                )
                errorPtr.value
            }
        if (categoryError != null) {
            Napier.e("Failed to set audio session category: ${categoryError.localizedDescription}")
            return false
        }

        val modeError =
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                audioSession.setMode(AVAudioSessionModeSpokenAudio, error = errorPtr.ptr)
                errorPtr.value
            }
        if (modeError != null) {
            Napier.e("Failed to set audio session mode: ${modeError.localizedDescription}")
            return false
        }

        val activateError =
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                audioSession.setActive(true, error = errorPtr.ptr)
                errorPtr.value
            }
        if (activateError != null) {
            Napier.e("Failed to activate audio session: ${activateError.localizedDescription}")
            return false
        }

        Napier.d("iOS audio session activated for recording")
        return true
    }

    /**
     * Releases the audio session so other apps' audio resumes promptly.
     */
    fun endAudioSession() {
        val error =
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                audioSession.setActive(
                    active = false,
                    withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
                    error = errorPtr.ptr,
                )
                errorPtr.value
            }
        if (error != null) {
            Napier.w("Failed to deactivate audio session: ${error.localizedDescription}")
        } else {
            Napier.d("Audio session deactivated")
        }
    }
}

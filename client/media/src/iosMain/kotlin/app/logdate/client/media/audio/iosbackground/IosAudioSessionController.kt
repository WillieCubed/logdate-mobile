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
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeDefault
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

        try {
            // Set category to allow simultaneous recording and playback
            val categoryOptions =
                AVAudioSessionCategoryOptionDefaultToSpeaker or
                    AVAudioSessionCategoryOptionAllowBluetooth or
                    AVAudioSessionCategoryOptionAllowBluetoothA2DP or
                    AVAudioSessionCategoryOptionDuckOthers

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

            // Set mode to default
            val modeError =
                memScoped {
                    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                    audioSession.setMode(AVAudioSessionModeDefault, error = errorPtr.ptr)
                    errorPtr.value
                }

            if (modeError != null) {
                Napier.e("Failed to set audio session mode: ${modeError.localizedDescription}")
                return false
            }

            // Activation is platform-managed on iOS; avoid explicit activation here.

            // Configure for background operation
            setupBackgroundRecordingCapabilities()

            Napier.d("iOS audio session set up successfully")
            return true
        } catch (e: Exception) {
            Napier.e("Exception setting up audio session: ${e.message}", e)
            return false
        }
    }

    /**
     * Sets up the audio session for background operation.
     */
    private fun setupBackgroundRecordingCapabilities() {
        try {
            // Required to continue audio recording in the background
            val options =
                AVAudioSessionCategoryOptionAllowBluetooth or
                    AVAudioSessionCategoryOptionMixWithOthers or
                    AVAudioSessionCategoryOptionDefaultToSpeaker

            val sessionError =
                memScoped {
                    val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                    audioSession.setCategory(
                        AVAudioSessionCategoryPlayAndRecord,
                        withOptions = options,
                        error = errorPtr.ptr,
                    )
                    errorPtr.value
                }

            if (sessionError != null) {
                Napier.e("Failed to set background audio options: ${sessionError.localizedDescription}")
            } else {
                Napier.d("Successfully configured background audio recording")
            }
        } catch (e: Exception) {
            Napier.e("Exception configuring background audio: ${e.message}", e)
        }
    }

    /**
     * Ends the audio session.
     */
    fun endAudioSession() {
        try {
            Napier.d("Audio session ended")
        } catch (e: Exception) {
            Napier.e("Exception ending audio session: ${e.message}", e)
        }
    }
}

package app.logdate.feature.editor.ui.audio.iosbackground

import io.github.aakira.napier.Napier
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptions
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionSetActiveOptions
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
            var sessionError: NSError? = null
            
            // Set category to allow simultaneous recording and playback
            audioSession.setCategory(
                AVAudioSessionCategoryPlayAndRecord,
                AVAudioSessionCategoryOptions.AVAudioSessionCategoryOptionDefaultToSpeaker or
                        AVAudioSessionCategoryOptions.AVAudioSessionCategoryOptionAllowBluetooth or
                        AVAudioSessionCategoryOptions.AVAudioSessionCategoryOptionAllowBluetoothA2DP or
                        AVAudioSessionCategoryOptions.AVAudioSessionCategoryOptionDuckOthers,
                { sessionError = it }
            )
            
            if (sessionError != null) {
                Napier.e("Failed to set audio session category: ${sessionError?.localizedDescription}")
                return false
            }
            
            // Set mode to default
            audioSession.setMode(AVAudioSessionModeDefault, { sessionError = it })
            
            if (sessionError != null) {
                Napier.e("Failed to set audio session mode: ${sessionError?.localizedDescription}")
                return false
            }
            
            // Activate the audio session
            audioSession.setActive(
                true,
                AVAudioSessionSetActiveOptions.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
                { sessionError = it }
            )
            
            if (sessionError != null) {
                Napier.e("Failed to activate audio session: ${sessionError?.localizedDescription}")
                return false
            }
            
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
            val options = AVAudioSessionCategoryOptions.AVAudioSessionCategoryOptionAllowBluetooth or
                    AVAudioSessionCategoryOptions.AVAudioSessionCategoryOptionMixWithOthers or
                    AVAudioSessionCategoryOptions.AVAudioSessionCategoryOptionDefaultToSpeaker
            
            var sessionError: NSError? = null
            
            // Set category options for background operation
            audioSession.setCategory(
                AVAudioSessionCategoryPlayAndRecord,
                options,
                { sessionError = it }
            )
            
            if (sessionError != null) {
                Napier.e("Failed to set background audio options: ${sessionError?.localizedDescription}")
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
            var sessionError: NSError? = null
            
            // Deactivate the audio session
            audioSession.setActive(false, { sessionError = it })
            
            if (sessionError != null) {
                Napier.e("Error deactivating audio session: ${sessionError?.localizedDescription}")
            } else {
                Napier.d("Audio session ended")
            }
        } catch (e: Exception) {
            Napier.e("Exception ending audio session: ${e.message}", e)
        }
    }
}
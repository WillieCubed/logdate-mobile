package app.logdate.client.media.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.logdate.client.media.device.AndroidAudioRouteDevices
import app.logdate.client.notifications.AndroidLogDateNotificationCatalog
import app.logdate.client.notifications.LogDateNotificationChannelKey

class AudioPlaybackService : MediaSessionService() {
    companion object {
        val CHANNEL_ID = LogDateNotificationChannelKey.AUDIO_PLAYBACK.id
        val NOTIFICATION_ID = LogDateNotificationChannelKey.AUDIO_PLAYBACK.notificationId ?: 2002
    }

    private var mediaSession: MediaSession? = null
    private var routePreferences: SharedPreferences? = null

    /**
     * Re-applies the chosen output whenever the user picks a different one. The picker writes the
     * preference from the app process; this service owns the player, so it has to hear about it.
     */
    private val routePreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyPreferredOutputDevice() }

    /**
     * Re-applies on hardware changes so unplugging the chosen device falls back to the system
     * default instead of leaving playback pinned to something no longer there.
     */
    private val routeDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = applyPreferredOutputDevice()

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = applyPreferredOutputDevice()
        }

    override fun onCreate() {
        super.onCreate()

        val player =
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .setSuppressPlaybackOnUnsuitableOutput(true)
                .build()

        val sessionActivity = createSessionActivity()
        val sessionBuilder = MediaSession.Builder(this, player)
        if (sessionActivity != null) {
            sessionBuilder.setSessionActivity(sessionActivity)
        }
        mediaSession = sessionBuilder.build()

        val notificationProvider =
            DefaultMediaNotificationProvider
                .Builder(this)
                .setNotificationId(NOTIFICATION_ID)
                .setChannelId(CHANNEL_ID)
                .setChannelName(AndroidLogDateNotificationCatalog.channel(LogDateNotificationChannelKey.AUDIO_PLAYBACK).nameResId)
                .build()
        setMediaNotificationProvider(notificationProvider)

        routePreferences =
            AndroidAudioRouteDevices.routePreferences(this).also {
                it.registerOnSharedPreferenceChangeListener(routePreferenceListener)
            }
        audioManager().registerAudioDeviceCallback(routeDeviceCallback, null)
        applyPreferredOutputDevice()

        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    updateSessionActivity(mediaItem?.mediaMetadata)
                }

                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    updateSessionActivity(mediaMetadata)
                }
            },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        routePreferences?.unregisterOnSharedPreferenceChangeListener(routePreferenceListener)
        routePreferences = null
        audioManager().unregisterAudioDeviceCallback(routeDeviceCallback)
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun audioManager(): AudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Points the player at the output the user chose.
     *
     * A preference naming a device that is gone resolves to null, which is Media3's way of
     * saying "use the system default" — so a disconnected pair of earbuds degrades to the
     * speaker rather than failing.
     */
    private fun applyPreferredOutputDevice() {
        val player = mediaSession?.player as? ExoPlayer ?: return
        player.setPreferredAudioDevice(AndroidAudioRouteDevices.findPreferredOutputDevice(this))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (mediaSession?.player?.isPlaying != true) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Builds a session activity PendingIntent that can deep-link to a note.
     */
    private fun createSessionActivity(noteId: String? = null): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (!noteId.isNullOrBlank()) {
            launchIntent.putExtra(EXTRA_NOTE_ID, noteId)
            launchIntent.putExtra(EXTRA_NAV_SOURCE, NAV_SOURCE_AUDIO_PLAYBACK)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, launchIntent, flags)
    }

    /**
     * Syncs the MediaSession activity with the current media item's metadata.
     */
    private fun updateSessionActivity(metadata: MediaMetadata?) {
        val noteId = metadata?.extras?.getString(EXTRA_NOTE_ID)
        val pendingIntent = createSessionActivity(noteId) ?: return
        mediaSession?.setSessionActivity(pendingIntent)
    }
}

package app.logdate.client.location.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.location.history.LocationTracker
import app.logdate.client.repository.location.LocationCapturePipeline
import app.logdate.client.repository.location.LocationCaptureSource
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

fun Context.startDetailedLocationTrackingService() {
    val intent =
        Intent(this, DetailedLocationTrackingService::class.java).apply {
            action = DetailedLocationTrackingService.ACTION_START
        }
    startForegroundService(intent)
}

fun Context.stopDetailedLocationTrackingService() {
    val intent =
        Intent(this, DetailedLocationTrackingService::class.java).apply {
            action = DetailedLocationTrackingService.ACTION_STOP
        }
    stopService(intent)
}

class DetailedLocationTrackingService :
    Service(),
    KoinComponent {
    companion object {
        const val ACTION_START = "app.logdate.location.action.START_DETAILED_TRACKING"
        const val ACTION_STOP = "app.logdate.location.action.STOP_DETAILED_TRACKING"
        private const val CHANNEL_ID = "logdate_location_detail_tracking"
        private const val NOTIFICATION_ID = 1904
        private const val SAMPLE_INTERVAL_SECONDS = 15L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locationProvider: ClientLocationProvider by inject()
    private val locationTracker: LocationTracker by inject()
    private var trackingJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                trackingJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            else -> startDetailedTracking()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        trackingJob?.cancel()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startDetailedTracking() {
        if (trackingJob != null) {
            return
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        trackingJob =
            serviceScope.launch {
                while (isActive) {
                    runCatching {
                        locationProvider.refreshLocation()
                        val location =
                            withTimeoutOrNull(20.seconds) {
                                locationProvider.getCurrentLocation()
                            }

                        if (location != null) {
                            locationTracker.logLocation(
                                location = location,
                                metadata =
                                    mapOf(
                                        "capturePipeline" to LocationCapturePipeline.HIGH_DETAIL,
                                        "captureSource" to LocationCaptureSource.FOREGROUND_STREAM,
                                    ),
                            )
                        }
                    }.onFailure { error ->
                        Napier.w("Detailed location tracking sample failed", error)
                    }

                    delay(SAMPLE_INTERVAL_SECONDS.seconds)
                }
            }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Location detail tracking",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps detailed activity tracking running in the background."
                setSound(null, null)
                enableVibration(false)
            }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent()
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return Notification
            .Builder(this, CHANNEL_ID)
            .setContentTitle("Detailed location tracking")
            .setContentText("Capturing a denser activity trace for comparison.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}

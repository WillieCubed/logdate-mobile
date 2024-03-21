package app.logdate.core.world

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import app.logdate.core.coroutines.AppDispatcher.Default
import app.logdate.core.coroutines.Dispatcher
import app.logdate.core.world.model.LogdateActivity
import app.logdate.model.UserPlace
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class FusedWorldProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(Default) private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ActivityLocationProvider, PlacesProvider {

    private val activityClient = ActivityRecognition.getClient(context)
    private val locationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var activityCallbackIntent: PendingIntent? = null

    private lateinit var cachedActivity: LogdateActivity
    private lateinit var cachedLastLocation: Location

    private companion object {
        val transitions: List<ActivityTransition> = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.UNKNOWN)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.UNKNOWN)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
        )

        const val UPDATE_INTERVAL_MILLISECONDS = 30_000L // 30 seconds
    }

    override fun getCurrentActivity(): LogdateActivity {
        return cachedActivity
    }

    override fun observeActivity(onActivityUpdate: (LogdateActivity) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        subscribeToActivityUpdates()
    }

    private fun subscribeToActivityUpdates() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val request = ActivityTransitionRequest(transitions)
        activityCallbackIntent?.let { activityClient.requestActivityTransitionUpdates(request, it) }
    }

    private fun endActivityUpdates() { // TODO: Find more elegant way to handle this
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        activityCallbackIntent?.let { activityClient.removeActivityTransitionUpdates(it) }
    }

    override fun updateActivity(activity: LogdateActivity) {
        cachedActivity = activity
    }

    override fun getCurrentLocation(): Location {
        return cachedLastLocation
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            runCatching {
                locationResult.lastLocation?.let {
                    cachedLastLocation = it
                }
            }.getOrDefault(false)
        }
    }

    @Throws(SecurityException::class)
    override fun observeLocation(): Flow<Location> = channelFlow {
        startObservingLocation()
        locationClient.lastLocation.await<Location?>()?.let { send(it) }
        awaitClose {
            stopObservingLocation()
        }
    }

    @Throws(SecurityException::class)
    private fun startObservingLocation() {
        val locationRequest = LocationRequest.Builder(UPDATE_INTERVAL_MILLISECONDS)
            .apply {
                setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            }.build()
        locationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    @Throws(SecurityException::class)
    private fun stopObservingLocation() {
        locationClient.removeLocationUpdates(locationCallback)
    }

    override fun resolvePlace(latitude: Double, longitude: Double): List<UserPlace> {
        return listOf()
    }

    override fun getNearbyPlaces(place: UserPlace): List<UserPlace> {
        return listOf()
    }

    override fun getNearbyPlaces(latitude: Double, longitude: Double): List<UserPlace> {
        return listOf()
    }
}

suspend fun <TResult : Any> Task<TResult>.toCoroutine(): TResult {
    return suspendCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
    }
}
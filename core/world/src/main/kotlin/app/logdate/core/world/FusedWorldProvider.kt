package app.logdate.core.world

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.coroutines.resume
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
    private lateinit var currentPlace: UserPlace
    private val cachedPlaces: MutableList<UserPlaceResult> = mutableListOf()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            runCatching {
                locationResult.lastLocation?.let {
                    cachedLastLocation = it
                }
            }.getOrDefault(false)
        }
    }

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

    override fun updateActivity(activity: LogdateActivity) {
        cachedActivity = activity
    }

    override fun getCurrentLocation(): Location {
        return cachedLastLocation
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

    override fun observeCurrentPlace(refresh: Boolean): Flow<UserPlace> {
        return channelFlow {
            if (refresh) {
                refreshCurrentPlace()
            }
            send(currentPlace)
            awaitClose()
        }
    }

    @Throws(SecurityException::class)
    override suspend fun refreshCurrentPlace() {
        // TODO: Get current location
        val location = locationClient.lastLocation.await()
        if (location != null) {
            val result = resolvePlace(location.latitude, location.longitude).firstOrNull()
            // TODO: Take into account confidence threshold
            if (result != null) {
                currentPlace = result.place
            }
        }
        return
    }

    override suspend fun resolvePlace(latitude: Double, longitude: Double): List<UserPlaceResult> =
        suspendCoroutine {
            val geocoder = Geocoder(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val listener = object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        cachedPlaces.clear()
                        addresses.forEach { address ->
                            val place = UserPlace.Unknown
                            val result = UserPlaceResult(place, 1)
                            cachedPlaces.add(result)
                        }
                        it.resume(cachedPlaces)
                    }

                    override fun onError(errorMessage: String?) {
                        super.onError(errorMessage)
                        Log.e("FusedWorldProvider", "Error resolving place: $errorMessage")
                    }
                }
                geocoder.getFromLocation(latitude, longitude, 5, listener)
            } else {
                @Suppress("DEPRECATION") val addresses =
                    geocoder.getFromLocation(latitude, longitude, 5)
                cachedPlaces.clear()
                addresses?.forEach { address ->
                    val place = UserPlace.Unknown
                    val result = UserPlaceResult(place, 1)
                    cachedPlaces.add(result)
                }
                it.resume(cachedPlaces)
            }

        }

    override suspend fun getNearbyPlaces(place: UserPlace): List<UserPlaceResult> {
        return listOf()
    }

    override suspend fun getNearbyPlaces(
        latitude: Double,
        longitude: Double
    ): List<UserPlaceResult> {
        return listOf()
    }
}

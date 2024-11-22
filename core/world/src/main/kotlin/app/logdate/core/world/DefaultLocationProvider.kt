package app.logdate.core.world

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import app.logdate.core.permission.AppPermission
import app.logdate.core.permission.PermissionProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class DefaultLocationProvider @Inject constructor(
    private val permissionProvider: PermissionProvider,
    context: Context,
) : LocationProvider {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            runCatching {
                locationResult.lastLocation?.let {
                    AbsoluteLocation(
                        it.latitude,
                        it.longitude,
                        it.altitude
                    )
                }
            }.onFailure {
                Log.e("LocationProvider", "Failed to get location", it)
            }
        }
    }

    /**
     * Returns the last known location.
     *
     * If the user hasn't granted the necessary permissions, the flow will emit null.
     */
    @SuppressLint("MissingPermission")
    override val lastLocation: Flow<AbsoluteLocation?> = flow {
        if (permissionProvider.hasPermission(AppPermission.PRECISE_LOCATION)) {
//            val locationRequest = fusedLocationClient.lastLocation
//            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
////            suspendCoroutine<AbsoluteLocation> {
////                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
////                    runBlocking {
////                        it.resume(
////                            AbsoluteLocation(
////                                location.latitude,
////                                location.longitude,
////                                location.altitude
////                            )
////                        )
////                    }
////                }.addOnFailureListener {  }
////            }
        } else {
            emit(null)
        }
    }

}


@Deprecated(
    "Use NewLocationProvider instead",
    replaceWith = ReplaceWith(
        "NewLocationProvider",
        "app.logdate.core.world.NewLocationProvider"
    )
)
interface LocationProvider {
    val lastLocation: Flow<AbsoluteLocation?>
}

data class AbsoluteLocation(val latitude: Double, val longitude: Double, val altitude: Double) {
}
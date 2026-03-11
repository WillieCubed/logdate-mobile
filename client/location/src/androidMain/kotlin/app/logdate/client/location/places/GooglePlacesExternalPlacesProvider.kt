package app.logdate.client.location.places

import android.content.Context
import android.content.pm.PackageManager
import app.logdate.client.location.BuildConfig
import app.logdate.shared.model.Location
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import io.github.aakira.napier.Napier
import kotlinx.coroutines.tasks.await

class GooglePlacesExternalPlacesProvider(
    private val context: Context,
    private val apiKey: String = BuildConfig.GOOGLE_MAPS_API_KEY,
) : ExternalPlacesProvider {
    private var hasLoggedMissingApiKey = false
    private val resolvedApiKey: String by lazy(::resolveApiKey)
    private val placesClient: PlacesClient? by lazy(::createPlacesClientOrNull)

    override suspend fun searchNearbyPlaces(location: Location): List<PlaceSuggestion> {
        if (resolvedApiKey.isBlank()) {
            logMissingApiKeyOnce()
            return emptyList()
        }

        val client = placesClient ?: return emptyList()

        return runCatching {
            val request =
                SearchNearbyRequest
                    .builder(
                        CircularBounds.newInstance(
                            LatLng(location.latitude, location.longitude),
                            250.0,
                        ),
                        listOf(
                            Place.Field.DISPLAY_NAME,
                            Place.Field.FORMATTED_ADDRESS,
                            Place.Field.LOCATION,
                            Place.Field.ID,
                            Place.Field.PRIMARY_TYPE,
                            Place.Field.PRIMARY_TYPE_DISPLAY_NAME,
                        ),
                    ).setMaxResultCount(5)
                    .setRankPreference(SearchNearbyRequest.RankPreference.DISTANCE)
                    .build()

            client
                .searchNearby(request)
                .await()
                .places
                .mapIndexedNotNull { index, place ->
                    val latLng = place.location ?: return@mapIndexedNotNull null
                    PlaceSuggestion(
                        name = place.displayName ?: place.primaryTypeDisplayName ?: place.formattedAddress ?: "Nearby place",
                        address = place.formattedAddress ?: "${latLng.latitude}, ${latLng.longitude}",
                        latitude = latLng.latitude,
                        longitude = latLng.longitude,
                        confidence = (95 - (index * 10)).coerceAtLeast(55),
                        category = place.primaryTypeDisplayName ?: place.primaryType,
                        externalId = place.id,
                    )
                }
        }.onFailure { error ->
            Napier.w("Google Places nearby search failed", error)
        }.getOrDefault(emptyList())
    }

    private fun createPlacesClientOrNull(): PlacesClient? {
        if (resolvedApiKey.isBlank()) {
            logMissingApiKeyOnce()
            return null
        }

        return runCatching {
            if (!Places.isInitialized()) {
                Places.initializeWithNewPlacesApiEnabled(context.applicationContext, resolvedApiKey)
            }
            Places.createClient(context.applicationContext)
        }.onFailure { error ->
            Napier.w("Failed to initialize Google Places client", error)
        }.getOrNull()
    }

    private fun resolveApiKey(): String =
        selectResolvedGoogleMapsApiKey(
            explicitApiKey = apiKey,
            manifestApiKey = context.readGoogleMapsManifestApiKey(),
            googleServicesApiKey = context.readGoogleServicesApiKey(),
        )

    private fun logMissingApiKeyOnce() {
        if (!hasLoggedMissingApiKey) {
            Napier.w("No Google Maps/Places API key is available; semantic external place lookup is disabled.")
            hasLoggedMissingApiKey = true
        }
    }
}

private fun Context.readGoogleServicesApiKey(): String = readStringResource("google_api_key")

private fun Context.readGoogleMapsManifestApiKey(): String? =
    runCatching {
        val metaData =
            packageManager
                .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData

        val resourceId = metaData?.getInt("com.google.android.geo.API_KEY") ?: 0
        when {
            resourceId != 0 -> readStringResource(resourceId).ifBlank { null }
            else -> metaData?.getString("com.google.android.geo.API_KEY")?.ifBlank { null }
        }
    }.getOrNull()

private fun Context.readStringResource(name: String): String {
    val resourceId = resources.getIdentifier(name, "string", packageName)
    return readStringResource(resourceId)
}

private fun Context.readStringResource(resourceId: Int): String =
    if (resourceId == 0) {
        ""
    } else {
        runCatching {
            getString(resourceId)
        }.getOrDefault("")
    }

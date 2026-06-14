@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.logdate.client.location.settings.DefaultLocation
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.platform.PlatformKind
import app.logdate.ui.platform.rememberPlatformKind
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.location_advanced
import logdate.client.feature.core.generated.resources.location_default_location
import logdate.client.feature.core.generated.resources.location_default_location_altitude
import logdate.client.feature.core.generated.resources.location_default_location_clear
import logdate.client.feature.core.generated.resources.location_default_location_description
import logdate.client.feature.core.generated.resources.location_default_location_invalid_altitude
import logdate.client.feature.core.generated.resources.location_default_location_invalid_latitude
import logdate.client.feature.core.generated.resources.location_default_location_invalid_longitude
import logdate.client.feature.core.generated.resources.location_default_location_latitude
import logdate.client.feature.core.generated.resources.location_default_location_longitude
import logdate.client.feature.core.generated.resources.location_default_location_save
import logdate.client.feature.core.generated.resources.location_server_assist
import logdate.client.feature.core.generated.resources.location_server_assist_description
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LocationAdvancedScreen(
    onBack: () -> Unit,
    viewModel: LocationSettingsViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LocationAdvancedContent(
        settings = uiState.settings,
        onBack = onBack,
        onToggleServerAssist = viewModel::toggleServerAssist,
        onSetDefaultLocation = viewModel::setDefaultLocation,
        modifier = modifier,
    )
}

@Composable
fun LocationAdvancedContent(
    settings: LocationTrackingSettings,
    onBack: () -> Unit,
    onToggleServerAssist: (Boolean) -> Unit,
    onSetDefaultLocation: (DefaultLocation?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val platformKind = rememberPlatformKind()

    FoldableBookLayout(
        modifier = modifier.fillMaxSize(),
        minPaneWidth = 320.dp,
        startPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                SettingsSection(
                    title = stringResource(Res.string.location_advanced),
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    ToggleSettingsItem(
                        title = stringResource(Res.string.location_server_assist),
                        description = stringResource(Res.string.location_server_assist_description),
                        checked = settings.serverAssistEnabled,
                        onCheckedChange = onToggleServerAssist,
                    )
                }
            }
        },
        endPane = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                if (platformKind == PlatformKind.Desktop) {
                    DefaultLocationSettingsSection(
                        defaultLocation = settings.defaultLocation,
                        onSetDefaultLocation = onSetDefaultLocation,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            }
        },
        singlePaneContent = {
            SettingsScaffold(
                title = stringResource(Res.string.location_advanced),
                onBack = onBack,
                modifier = modifier,
            ) {
                item {
                    SettingsSection(
                        title = stringResource(Res.string.location_advanced),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        ToggleSettingsItem(
                            title = stringResource(Res.string.location_server_assist),
                            description = stringResource(Res.string.location_server_assist_description),
                            checked = settings.serverAssistEnabled,
                            onCheckedChange = onToggleServerAssist,
                        )
                    }
                }

                if (platformKind == PlatformKind.Desktop) {
                    item {
                        DefaultLocationSettingsSection(
                            defaultLocation = settings.defaultLocation,
                            onSetDefaultLocation = onSetDefaultLocation,
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }
                }
            }
        },
    )
}

/**
 * Desktop-only fallback location editor for devices without reliable location services.
 */
@Composable
private fun DefaultLocationSettingsSection(
    defaultLocation: DefaultLocation?,
    onSetDefaultLocation: (DefaultLocation?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var latitude by
        remember(defaultLocation) {
            mutableStateOf(defaultLocation?.latitude?.toString().orEmpty())
        }
    var longitude by
        remember(defaultLocation) {
            mutableStateOf(defaultLocation?.longitude?.toString().orEmpty())
        }
    var altitude by
        remember(defaultLocation) {
            mutableStateOf(defaultLocation?.altitudeValue?.toString().orEmpty())
        }
    val validation =
        validateDefaultLocationInput(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
        )

    SettingsSection(
        title = stringResource(Res.string.location_default_location),
        modifier = modifier,
    ) {
        Text(stringResource(Res.string.location_default_location_description))
        OutlinedTextField(
            value = latitude,
            onValueChange = { latitude = it },
            label = { Text(stringResource(Res.string.location_default_location_latitude)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = validation.latitudeError != null,
            supportingText = validation.latitudeError?.let { error -> { Text(error.message()) } },
            singleLine = true,
        )
        OutlinedTextField(
            value = longitude,
            onValueChange = { longitude = it },
            label = { Text(stringResource(Res.string.location_default_location_longitude)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = validation.longitudeError != null,
            supportingText = validation.longitudeError?.let { error -> { Text(error.message()) } },
            singleLine = true,
        )
        OutlinedTextField(
            value = altitude,
            onValueChange = { altitude = it },
            label = { Text(stringResource(Res.string.location_default_location_altitude)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = validation.altitudeError != null,
            supportingText = validation.altitudeError?.let { error -> { Text(error.message()) } },
            singleLine = true,
        )
        Button(
            enabled = validation.canSave,
            onClick = {
                validation.location?.let(onSetDefaultLocation)
            },
        ) {
            Text(stringResource(Res.string.location_default_location_save))
        }
        TextButton(onClick = { onSetDefaultLocation(null) }) {
            Text(stringResource(Res.string.location_default_location_clear))
        }
    }
}

internal data class DefaultLocationInputValidation(
    val location: DefaultLocation?,
    val latitudeError: DefaultLocationInputError? = null,
    val longitudeError: DefaultLocationInputError? = null,
    val altitudeError: DefaultLocationInputError? = null,
) {
    val canSave: Boolean =
        location != null &&
            latitudeError == null &&
            longitudeError == null &&
            altitudeError == null
}

internal enum class DefaultLocationInputError {
    InvalidLatitude,
    InvalidLongitude,
    InvalidAltitude,
}

internal fun validateDefaultLocationInput(
    latitude: String,
    longitude: String,
    altitude: String,
): DefaultLocationInputValidation {
    val parsedLatitude = latitude.trim().toDoubleOrNull()
    val parsedLongitude = longitude.trim().toDoubleOrNull()
    val trimmedAltitude = altitude.trim()
    val parsedAltitude = if (trimmedAltitude.isBlank()) 0.0 else trimmedAltitude.toDoubleOrNull()

    val latitudeError =
        if (parsedLatitude == null || parsedLatitude !in -90.0..90.0) {
            DefaultLocationInputError.InvalidLatitude
        } else {
            null
        }
    val longitudeError =
        if (parsedLongitude == null || parsedLongitude !in -180.0..180.0) {
            DefaultLocationInputError.InvalidLongitude
        } else {
            null
        }
    val altitudeError =
        if (parsedAltitude == null) {
            DefaultLocationInputError.InvalidAltitude
        } else {
            null
        }

    val location =
        if (latitudeError == null && longitudeError == null && altitudeError == null) {
            DefaultLocation(
                latitude = checkNotNull(parsedLatitude),
                longitude = checkNotNull(parsedLongitude),
                altitudeValue = checkNotNull(parsedAltitude),
            )
        } else {
            null
        }

    return DefaultLocationInputValidation(
        location = location,
        latitudeError = latitudeError,
        longitudeError = longitudeError,
        altitudeError = altitudeError,
    )
}

@Composable
private fun DefaultLocationInputError.message(): String =
    when (this) {
        DefaultLocationInputError.InvalidLatitude ->
            stringResource(Res.string.location_default_location_invalid_latitude)
        DefaultLocationInputError.InvalidLongitude ->
            stringResource(Res.string.location_default_location_invalid_longitude)
        DefaultLocationInputError.InvalidAltitude ->
            stringResource(Res.string.location_default_location_invalid_altitude)
    }

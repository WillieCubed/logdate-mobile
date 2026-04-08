@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.logdate.feature.rewind.ui.WeatherChipCategory
import app.logdate.feature.rewind.ui.WeatherChipUiState
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.weather_chip_cloudy
import logdate.client.feature.rewind.generated.resources.weather_chip_mixed
import logdate.client.feature.rewind.generated.resources.weather_chip_rainy
import logdate.client.feature.rewind.generated.resources.weather_chip_snowy
import logdate.client.feature.rewind.generated.resources.weather_chip_sunny
import logdate.client.feature.rewind.generated.resources.weather_chip_temperature
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * A small overlay chip on the title panel that hints at what the week's weather was
 * actually like outdoors.
 *
 * Quietly placed in the top-end corner so it grounds the rewind in real conditions
 * without competing with the title text. Renders an icon for the dominant condition
 * plus a one-line label and the rounded average temperature in Celsius. Temperature
 * unit conversion lives here so the ViewModel doesn't need to reach into the locale
 * or compose-resources.
 */
@Composable
fun WeatherChip(
    state: WeatherChipUiState,
    modifier: Modifier = Modifier,
) {
    val labelRes =
        when (state.category) {
            WeatherChipCategory.SUNNY -> Res.string.weather_chip_sunny
            WeatherChipCategory.CLOUDY -> Res.string.weather_chip_cloudy
            WeatherChipCategory.RAINY -> Res.string.weather_chip_rainy
            WeatherChipCategory.SNOWY -> Res.string.weather_chip_snowy
            WeatherChipCategory.MIXED -> Res.string.weather_chip_mixed
        }
    val icon: ImageVector =
        when (state.category) {
            WeatherChipCategory.SUNNY -> Icons.Filled.WbSunny
            WeatherChipCategory.CLOUDY -> Icons.Filled.Cloud
            WeatherChipCategory.RAINY -> Icons.Filled.Grain
            WeatherChipCategory.SNOWY -> Icons.Filled.AcUnit
            WeatherChipCategory.MIXED -> Icons.Filled.Thermostat
        }
    val tempLabel = stringResource(Res.string.weather_chip_temperature, state.avgTempCelsius.roundToInt())
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(PaddingValues(horizontal = 12.dp, vertical = 6.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(labelRes),
            tint = Color.White,
            modifier = Modifier,
        )
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
        Text(
            text = tempLabel,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

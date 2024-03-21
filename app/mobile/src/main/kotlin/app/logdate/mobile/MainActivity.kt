package app.logdate.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.logdate.mobile.ui.LogdateAppRoot
import app.logdate.mobile.ui.common.rememberMainAppState
import app.logdate.ui.theme.LogDateTheme
import dagger.hilt.android.AndroidEntryPoint

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appState = rememberMainAppState(
                windowSizeClass = calculateWindowSizeClass(this),
            )
            LogDateTheme {
                // A surface container using the 'background' color from the theme
                LogdateAppRoot(appState)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Preview(showBackground = true, name = "App Preview - Phone")
@Composable
fun AppPreview_Mobile() {
    LogDateTheme {
        val appState = rememberMainAppState(
            windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(412.dp, 918.dp)),
        )
        LogdateAppRoot(appState)
    }
}
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Preview(showBackground = true, name = "App Preview - Tablet", device = "id:pixel_tablet")
@Composable
fun AppPreview_Tablet() {
    LogDateTheme {
        val appState = rememberMainAppState(
            windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(1280.dp, 720.dp)),
        )
        LogdateAppRoot(appState)
    }
}
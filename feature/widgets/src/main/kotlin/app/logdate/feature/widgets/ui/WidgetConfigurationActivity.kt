package app.logdate.feature.widgets.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.logdate.ui.theme.LogDateTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * An activity for configuring LogDate widgets.
 */
@AndroidEntryPoint
class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LogDateTheme {
                // TODO: Implement widget configuration UI
            }
        }
    }
}
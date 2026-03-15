package app.logdate.wear.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.presentation.audio.AudioRecordingScreen
import app.logdate.wear.presentation.home.WearHomeScreen
import app.logdate.wear.presentation.mood.MoodCheckInScreen
import app.logdate.wear.presentation.navigation.WearAudioRecordingRoute
import app.logdate.wear.presentation.navigation.WearHomeRoute
import app.logdate.wear.presentation.navigation.WearMoodCheckInRoute
import app.logdate.wear.presentation.navigation.WearQuickTextRoute
import app.logdate.wear.presentation.navigation.WearWalkieTalkieRoute
import app.logdate.wear.presentation.quicktext.QuickTextLauncher
import app.logdate.wear.presentation.theme.LogDateTheme
import app.logdate.wear.presentation.walkietalkie.WalkieTalkieScreen
import io.github.aakira.napier.Napier
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Napier.d("All required permissions granted")
        } else {
            Napier.w("Some permissions were denied: $permissions")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            WearApp()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }
}

@Composable
fun WearApp() {
    LogDateTheme {
        val backStack = remember { mutableStateListOf<NavKey>(WearHomeRoute) }
        val navigateBack: () -> Unit = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        }

        NavDisplay(
            backStack = backStack,
            onBack = navigateBack,
            entryProvider = entryProvider {
                entry<WearHomeRoute> {
                    WearHomeScreen(
                        onNavigateToWalkieTalkie = {
                            backStack.add(WearWalkieTalkieRoute)
                        },
                        onNavigateToVoiceNote = {
                            backStack.add(WearAudioRecordingRoute)
                        },
                        onNavigateToMoodCheckIn = {
                            backStack.add(WearMoodCheckInRoute)
                        },
                        onNavigateToQuickText = {
                            backStack.add(WearQuickTextRoute)
                        },
                        onNavigateToTimeline = { /* Phase 5 */ },
                        onNavigateToSettings = { /* Phase 10 */ },
                    )
                }
                entry<WearAudioRecordingRoute> {
                    AudioRecordingScreen(onNavigateBack = navigateBack)
                }
                entry<WearWalkieTalkieRoute> {
                    WalkieTalkieScreen(onNavigateBack = navigateBack)
                }
                entry<WearMoodCheckInRoute> {
                    MoodCheckInScreen(
                        onNavigateBack = navigateBack,
                        onNavigateToVoiceNote = {
                            navigateBack()
                            backStack.add(WearAudioRecordingRoute)
                        },
                    )
                }
                entry<WearQuickTextRoute> {
                    val notesRepository = koinInject<JournalNotesRepository>()
                    QuickTextLauncher(
                        notesRepository = notesRepository,
                        onDone = navigateBack,
                    )
                }
            },
        )
    }
}

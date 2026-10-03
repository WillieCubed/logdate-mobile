package app.logdate.wear.di

import android.content.Context
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.ui.platform.createAndroidPlatformHapticsController
import app.logdate.ui.platform.isSystemHapticFeedbackOff
import app.logdate.wear.v2.haptics.HapticsPreferenceStore
import app.logdate.wear.v2.haptics.SharedPreferencesHapticsPreferenceStore
import app.logdate.wear.v2.haptics.watchHaptics
import org.koin.dsl.module

/** Koin module for the redesigned watch app. Only the new app's screens resolve anything from it. */
val wearV2Module =
    module {
        single<HapticsPreferenceStore> {
            SharedPreferencesHapticsPreferenceStore(
                get<Context>().getSharedPreferences(SharedPreferencesHapticsPreferenceStore.PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
        }
        single<LogDateHaptics> {
            val context = get<Context>()
            watchHaptics(
                controller = createAndroidPlatformHapticsController(context),
                preferences = get(),
                systemHapticsOff = { isSystemHapticFeedbackOff(context) },
            )
        }
    }

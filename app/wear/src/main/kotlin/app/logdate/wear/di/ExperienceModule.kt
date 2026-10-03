package app.logdate.wear.di

import app.logdate.wear.launch.WearExperienceGate
import org.koin.dsl.module

/** Koin module for deciding which watch app the activity shows. */
val wearExperienceModule =
    module {
        factory { WearExperienceGate(get()) }
    }

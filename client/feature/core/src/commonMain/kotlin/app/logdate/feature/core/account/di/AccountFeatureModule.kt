package app.logdate.feature.core.account.di

import org.koin.dsl.module

/**
 * Koin module for account feature dependencies.
 *
 * Legacy per-route ViewModels have been removed. The unified
 * [CloudAccountOnboardingViewModel][app.logdate.feature.core.account.CloudAccountOnboardingViewModel]
 * is registered in `CoreFeatureModule.android.kt`.
 */
val accountFeatureModule =
    module {
    }

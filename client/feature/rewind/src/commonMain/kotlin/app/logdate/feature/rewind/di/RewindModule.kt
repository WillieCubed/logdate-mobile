package app.logdate.feature.rewind.di

import app.logdate.client.domain.di.domainModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Module for Rewind functionality.
 */
val rewindFeatureModule: Module = module {
    includes(domainModule)
}
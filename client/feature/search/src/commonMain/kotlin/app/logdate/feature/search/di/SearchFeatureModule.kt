package app.logdate.feature.search.di

import app.logdate.client.domain.di.domainModule
import app.logdate.feature.search.ui.SearchViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Module for search functionality.
 */
val searchFeatureModule: Module = module {
    includes(domainModule)

    // ViewModels
    viewModelOf(::SearchViewModel)
}

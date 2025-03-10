package app.logdate.feature.editor.di

import app.logdate.client.domain.di.domainModule
import app.logdate.client.media.di.mediaModule
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * A module that provides the dependencies for the editor feature.
 */
val editorFeatureModule: Module = module {
    includes(domainModule)
    includes(mediaModule)

    viewModel { EntryEditorViewModel(get(), /*get(), get()*/) }
}
package app.logdate.feature.postcards.di

import app.logdate.feature.postcards.ui.CanvasEditorViewModel
import app.logdate.feature.postcards.ui.ExportViewModel
import app.logdate.feature.postcards.ui.PostcardViewerViewModel
import app.logdate.feature.postcards.ui.PostcardsCollectionViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val postcardsFeatureModule =
    module {
        includes(postcardsPlatformModule)
        viewModelOf(::PostcardViewerViewModel)
        viewModelOf(::CanvasEditorViewModel)
        viewModelOf(::PostcardsCollectionViewModel)
        viewModelOf(::ExportViewModel)
    }

package app.logdate.feature.stickers.di

import app.logdate.client.database.dao.StickerDao
import app.logdate.client.media.storage.MediaClaimFence
import app.logdate.feature.stickers.ui.StickerExtractionViewModel
import app.logdate.feature.stickers.ui.StickerRepository
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val stickersFeatureModule =
    module {
        viewModelOf(::StickerExtractionViewModel)
        single { StickerRepository(get<StickerDao>(), getOrNull<MediaClaimFence>() ?: MediaClaimFence.Disabled) }
    }

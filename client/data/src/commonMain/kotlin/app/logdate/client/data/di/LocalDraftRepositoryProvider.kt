package app.logdate.client.data.di

import app.logdate.client.data.journals.LocalFirstDraftRepository
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.repository.journals.DraftRepository
import org.koin.core.scope.Scope

/** All platforms wait for persisted origin before resolving local draft custody. */
internal fun Scope.createLocalDraftRepository(): DraftRepository =
    LocalFirstDraftRepository(
        get(),
        get(),
        get<app.logdate.client.datastore.SessionStorage>(),
        get<app.logdate.shared.config.LogDateConfigRepository>(),
        canonicalOwner = get(),
        metadata = get(),
        transactions = get(),
        configurationReady = { get<LogDateConfigDataSource>().awaitConfigurationLoaded() },
    )

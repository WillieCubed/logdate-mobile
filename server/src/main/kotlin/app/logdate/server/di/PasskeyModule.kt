package app.logdate.server.di

import app.logdate.server.database.PostgreSQLPasskeyRepository
import app.logdate.server.database.PostgreSQLRestoreCredentialRepository
import app.logdate.server.identity.AtprotoIdentityConfig
import app.logdate.server.passkeys.InMemoryPasskeyRepository
import app.logdate.server.passkeys.InMemoryRestoreCredentialRepository
import app.logdate.server.passkeys.PasskeyRepository
import app.logdate.server.passkeys.RestoreCredentialRepository
import app.logdate.server.passkeys.RestoreCredentialService
import app.logdate.server.passkeys.WebAuthnConfig
import app.logdate.server.passkeys.WebAuthnPasskeyService
import app.logdate.server.routes.AssetLinksConfig
import org.koin.core.module.Module

internal fun Module.passkeyServices(isDatabaseAvailable: Boolean) {
    single<PasskeyRepository> {
        if (isDatabaseAvailable) PostgreSQLPasskeyRepository() else InMemoryPasskeyRepository()
    }

    single<RestoreCredentialRepository> {
        if (isDatabaseAvailable) PostgreSQLRestoreCredentialRepository() else InMemoryRestoreCredentialRepository()
    }

    single { WebAuthnConfig.fromEnvironment(serverOrigin = get<AtprotoIdentityConfig>().pdsServiceEndpoint) }
    single { AssetLinksConfig.fromEnvironment() }
    single {
        val webAuthnConfig: WebAuthnConfig = get()
        WebAuthnPasskeyService(
            passkeyRepository = get(),
            relyingPartyId = webAuthnConfig.relyingPartyId,
            relyingPartyName = webAuthnConfig.relyingPartyName,
            origins = webAuthnConfig.origins,
        )
    }
    single {
        val webAuthnConfig: WebAuthnConfig = get()
        RestoreCredentialService(
            restoreCredentialRepository = get(),
            relyingPartyId = webAuthnConfig.relyingPartyId,
            relyingPartyName = webAuthnConfig.relyingPartyName,
            origins = webAuthnConfig.origins,
        )
    }
}

package app.logdate.client.sync.v2.link

import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.networking.DeviceLinkApiClient
import app.logdate.client.sync.cloud.LogDateCloudApiClient
import app.logdate.client.sync.cloud.RefreshedSession
import app.logdate.client.sync.cloud.SessionRefreshingAccountRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.config.PinnedLogDateConfigRepository
import io.ktor.client.HttpClient

/** Captures both link and refresh endpoints before either request can suspend. */
class PhoneLinkClientFactory(
    private val config: LogDateConfigRepository,
    private val http: HttpClient,
) {
    fun capture(bound: OriginBoundSession): LinkCodeMinter.Clients? {
        val version = config.apiVersion.value
        if (config.getCurrentBackendUrl() != bound.origin) return null
        val pinned = PinnedLogDateConfigRepository(bound.origin, descriptor = null, apiVersion = version)
        val cloud = LogDateCloudApiClient(pinned, http)
        return LinkCodeMinter.Clients(
            DeviceLinkApiClient(http, pinned),
            object : SessionRefreshingAccountRepository {
                override suspend fun refreshSession(refreshToken: String): Result<RefreshedSession> = cloud.refreshSession(refreshToken)
            },
        )
    }
}

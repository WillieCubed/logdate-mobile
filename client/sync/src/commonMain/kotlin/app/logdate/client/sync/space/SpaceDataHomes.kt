package app.logdate.client.sync.space

import app.logdate.space.DataHome
import app.logdate.space.XrpcDataHome
import io.ktor.client.HttpClient
import studio.hypertext.atproto.xrpc.BearerToken
import studio.hypertext.atproto.xrpc.KtorXrpcClient

/**
 * Opens the data home on a server, signed in with one access token. A device can change servers,
 * and a refreshed token replaces the old one, so a data home is opened for each request rather
 * than kept.
 */
fun interface SpaceDataHomes {
    /**
     * @param origin the server's origin, such as `https://cloud.logdate.app`; its space methods are
     *   at `{origin}/xrpc/...`, not under the REST API's base path.
     */
    fun open(
        origin: String,
        accessToken: String,
    ): DataHome
}

/** [SpaceDataHomes] over the app's HTTP client. */
class XrpcSpaceDataHomes(
    private val httpClient: HttpClient,
) : SpaceDataHomes {
    override fun open(
        origin: String,
        accessToken: String,
    ): DataHome {
        val base = origin.trimEnd('/')
        return XrpcDataHome(KtorXrpcClient(httpClient, base), httpClient, base) { BearerToken(accessToken) }
    }
}

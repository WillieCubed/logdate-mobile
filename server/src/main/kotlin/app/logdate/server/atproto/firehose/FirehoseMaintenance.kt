package app.logdate.server.atproto.firehose

import app.logdate.server.identity.AtprotoIdentityService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.hours

/**
 * Starts the background work of the repository event stream when it is turned on: connecting
 * identity changes to the stream, asking the configured relays to crawl the server, and deleting
 * events older than the retention period. Does nothing while `ATPROTO_FIREHOSE_ENABLED` is off.
 */
internal fun Application.installFirehoseMaintenance() {
    val config by inject<AtprotoFirehoseConfig>()
    if (!config.enabled) return
    val identity by inject<AtprotoIdentityService>()
    val announcer by inject<PublicRepoAnnouncer>()
    val stream by inject<RepoEventStream>()
    val crawler by inject<RelayCrawler>()

    identity.setIdentityEvents(announcer)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    scope.launch { stream.pruneForever(PRUNE_INTERVAL) }
    scope.launch { crawler.requestAtStartup() }
    monitor.subscribe(ApplicationStopped) { scope.cancel() }
}

private val PRUNE_INTERVAL = 1.hours

package app.logdate.client.sync.diagnostics

import app.logdate.client.sync.cloud.DefaultCloudContentDataSource
import app.logdate.client.sync.test.fakeCloudApiClient
import app.logdate.client.sync.test.testDefaultSyncManager
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

class SyncLogPrivacyTest {
    @Test
    fun `sync failures cannot copy private exception causes to logging sinks`() =
        runTest {
            val messages = mutableListOf<String>()
            val sink =
                object : Antilog() {
                    override fun performLog(
                        priority: LogLevel,
                        tag: String?,
                        throwable: Throwable?,
                        message: String?,
                    ) {
                        messages += message.orEmpty() + throwable?.stackTraceToString().orEmpty()
                    }
                }
            Napier.base(sink)
            try {
                val api =
                    fakeCloudApiClient { getContentChangesResponse = Result.failure(IllegalStateException("private-content-and-path")) }
                testDefaultSyncManager(cloudContentDataSource = DefaultCloudContentDataSource(api)).downloadRemoteChanges()
                assertFalse(messages.joinToString().contains("private-"))
            } finally {
                Napier.takeLogarithm(sink)
            }
        }
}

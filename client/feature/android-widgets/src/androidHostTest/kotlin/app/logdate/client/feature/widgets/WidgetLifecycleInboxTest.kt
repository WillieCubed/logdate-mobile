package app.logdate.client.feature.widgets

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class WidgetLifecycleInboxTest {
    @Test
    fun `rejected scheduling retries and recreation retain a single immutable refresh`() =
        withInbox { directory ->
            var request = WidgetSetupRequest.photoPrompt(7, PhotoWidgetShape.CIRCLE)
            repeat(8) {
                assertFalse(
                    retainWidgetLifecycleCustody(
                        persist = { WidgetLifecycleInbox(directory).append(widgetRefreshEvent(request.refreshEventId)) },
                        schedule = { false },
                        persistedFallbackSupported = true,
                        onFailure = { throw it },
                    ),
                )
                val retained = WidgetLifecycleInbox(directory).pending().single().event
                assertEquals(request.refreshEventId, retained.id)
                assertTrue(retained.ids.isEmpty(), "Refresh resolves current registrations when delivered")
                request = requireNotNull(WidgetSetupRequest.decodeFor(request.encode(), 7, request.target))
            }
        }

    @Test
    fun `unreadable source cannot lose the mapping or mark settings applied`() =
        withInbox { directory ->
            val store = WidgetLifecycleInbox(directory)
            val generation = Uuid.random().toString()
            val unresolved = event().copy(sourceResolved = false, sourceGeneration = generation)
            store.append(unresolved)
            assertFailsWith<IllegalStateException> { store.settingsApplied(unresolved.id) }
            assertFailsWith<IllegalStateException> { store.acknowledge(unresolved.id) }
            assertEquals(unresolved, WidgetLifecycleInbox(directory).pending().single().event)
            val source = mapOf(1 to WidgetSettingValues(strings = mapOf("chosenNoteId" to "retained source")))
            WidgetLifecycleInbox(directory).resolveSource(unresolved.id, source)
            assertEquals(
                source,
                store
                    .pending()
                    .single()
                    .event.sourceValues,
            )
            assertTrue(
                store
                    .pending()
                    .single()
                    .event.sourceResolved,
            )
            assertEquals(
                generation,
                store
                    .pending()
                    .single()
                    .event.sourceGeneration,
            )
            assertFailsWith<IllegalArgumentException> { store.resolveSource(unresolved.id, emptyMap()) }
            store.settingsApplied(unresolved.id)
            store.acknowledge(unresolved.id)
            assertTrue(store.pending().isEmpty())
        }

    @Test
    fun `pending settings and immutable source choices survive a new store instance`() =
        withInbox { directory ->
            val event = event().copy(sourceValues = mapOf(2 to WidgetSettingValues(strings = mapOf("chosenNoteId" to "source entry"))))
            WidgetLifecycleInbox(directory).append(event)
            assertEquals(event, WidgetLifecycleInbox(directory).pending().single().event)
            WidgetLifecycleInbox(directory).settingsApplied(event.id)
            assertTrue(WidgetLifecycleInbox(directory).pending().single().settingsApplied)
            WidgetLifecycleInbox(directory).acknowledge(event.id)
            assertTrue(WidgetLifecycleInbox(directory).pending().isEmpty())
        }

    @Test
    fun `a stale completion cannot acknowledge a newer event after a full drain`() =
        withInbox { directory ->
            val store = WidgetLifecycleInbox(directory)
            val old = event()
            store.append(old)
            store.settingsApplied(old.id)
            store.acknowledge(old.id)
            val replacement = event()
            store.append(replacement)
            store.acknowledge(old.id)
            assertEquals(replacement, store.pending().single().event)
        }

    @Test
    fun `concurrent ingress preserves every event in a single order`() =
        withInbox { directory ->
            val start = CountDownLatch(1)
            val events = (1..24).map { event() }
            val threads =
                events.map { event ->
                    Thread {
                        start.await()
                        WidgetLifecycleInbox(directory).append(event)
                    }.apply { start() }
                }
            start.countDown()
            threads.forEach(Thread::join)
            val pending = WidgetLifecycleInbox(directory).pending()
            assertEquals(events.toSet(), pending.map { it.event }.toSet())
            assertEquals(24, pending.map { it.sequence }.distinct().size)
            assertEquals(pending.map { it.sequence }.sorted(), pending.map { it.sequence })
        }

    @Test
    fun `duplicate persistence retains the original payload and completion state`() =
        withInbox { directory ->
            val store = WidgetLifecycleInbox(directory)
            val event = event()
            store.append(event)
            store.settingsApplied(event.id)
            store.append(event)
            assertTrue(store.pending().single().settingsApplied)
            assertFailsWith<IllegalArgumentException> { store.append(event.copy(ids = listOf(99))) }
            assertEquals(event, store.pending().single().event)
        }

    @Test
    fun `partial unpublished writes cannot become events or hide published events`() =
        withInbox { directory ->
            val store = WidgetLifecycleInbox(directory)
            val event = event()
            store.append(event)
            File(directory, "unfinished.part").writeText("{broken")
            assertEquals(event, store.pending().single().event)
            val path = File(directory, "${event.id}.json")
            path.writeText("{broken")
            assertFailsWith<IllegalArgumentException> { store.pending() }
            assertTrue(path.exists())
        }

    @Test
    fun `unsafe identifiers cannot escape the private inbox`() =
        withInbox { directory ->
            val store = WidgetLifecycleInbox(directory)
            for (id in listOf("../escape", "", "not-a-uuid")) {
                assertFailsWith<IllegalArgumentException> { store.append(event().copy(id = id)) }
                assertFailsWith<IllegalArgumentException> { store.acknowledge(id) }
            }
            assertFalse(File(directory.parentFile, "escape.json").exists())
        }

    private fun event() = WidgetLifecycleEvent(Uuid.random().toString(), "FixedMemory", "restore", ids = listOf(2), oldIds = listOf(1))

    private fun withInbox(test: (File) -> Unit) {
        val directory = Files.createTempDirectory("widget-inbox-").toFile()
        try {
            test(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}

package app.logdate.server.routes

import app.logdate.server.sync.SyncMetricsRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncRouteBehaviorContractTest {
    @Test
    fun `private sync snapshots expose expected getters`() {
        val statusClass = Class.forName("app.logdate.server.routes.SyncStatusSnapshot")
        val status =
            statusClass
                .getDeclaredConstructor(Int::class.java, Int::class.java, Int::class.java, Long::class.java)
                .newInstance(1, 2, 3, 4L)
        assertEquals(1, statusClass.getMethod("getContentCount").invoke(status))
        assertEquals(2, statusClass.getMethod("getJournalCount").invoke(status))
        assertEquals(3, statusClass.getMethod("getAssociationCount").invoke(status))
        assertEquals(4L, statusClass.getMethod("getLastTimestamp").invoke(status))

        val purgeClass = Class.forName("app.logdate.server.routes.SyncPurgeResponse")
        val purge =
            purgeClass
                .getDeclaredConstructor(
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Long::class.java,
                    Long::class.java,
                ).newInstance(1, 2, 3, 4, 5L, 6L)
        assertEquals(1, purgeClass.getMethod("getContentPurged").invoke(purge))
        assertEquals(2, purgeClass.getMethod("getJournalPurged").invoke(purge))
        assertEquals(3, purgeClass.getMethod("getAssociationPurged").invoke(purge))
        assertEquals(4, purgeClass.getMethod("getMediaPurged").invoke(purge))
        assertEquals(5L, purgeClass.getMethod("getCutoff").invoke(purge))
        assertEquals(6L, purgeClass.getMethod("getRetentionDaysApplied").invoke(purge))
    }

    @Test
    fun `sync metrics prometheus helpers escape label values`() {
        val registry = SyncMetricsRegistry()
        registry.recordOperation("""sync."media"\test""", durationMs = 10, success = true, bytes = 5)
        val snapshot = registry.snapshot()

        val ktClass = Class.forName("app.logdate.server.routes.sync.SyncHelpersKt")
        val toPrometheus =
            ktClass.getDeclaredMethod(
                "toPrometheus",
                Class.forName("app.logdate.server.sync.SyncMetricsSnapshot"),
            )
        toPrometheus.isAccessible = true
        val output = toPrometheus.invoke(null, snapshot) as String
        assertTrue(output.contains("operation=\"sync.\\\"media\\\"\\\\test\""))

        val escape = ktClass.getDeclaredMethod("escapeLabelValue", String::class.java)
        escape.isAccessible = true
        val escaped = escape.invoke(null, """a"b\c""") as String
        assertEquals("""a\"b\\c""", escaped)
    }

    @Test
    fun `association link request constructors are invocable`() {
        val requestClass = Class.forName("app.logdate.server.routes.AssociationLinkUpsertRequest")
        val primaryCtor =
            requestClass.getDeclaredConstructor(
                Long::class.javaPrimitiveType,
                String::class.java,
                Class.forName("kotlin.jvm.internal.DefaultConstructorMarker"),
            )
        primaryCtor.isAccessible = true
        val explicit = primaryCtor.newInstance(1L, "dev-1", null)
        assertEquals(1L, requestClass.getMethod("getCreatedAt").invoke(explicit))
        assertEquals("dev-1", requestClass.getMethod("getDeviceId--pvMwk0").invoke(explicit))

        val defaultCtor =
            requestClass.getDeclaredConstructor(
                Long::class.javaPrimitiveType,
                String::class.java,
                Int::class.javaPrimitiveType,
                Class.forName("kotlin.jvm.internal.DefaultConstructorMarker"),
            )
        defaultCtor.isAccessible = true
        val withDefaultDevice = defaultCtor.newInstance(2L, null, 2, null)
        assertEquals(2L, requestClass.getMethod("getCreatedAt").invoke(withDefaultDevice))
    }
}

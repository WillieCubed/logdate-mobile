package app.logdate.client.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AndroidMainDataStoreRegistryTest {
    @Test
    fun `concurrent app and native callbacks share the canonical preferences file`() =
        runTest {
            val root = createTempDirectory("main-preferences").toFile()
            try {
                File(root, "alias").mkdir()
                val registry =
                    AndroidMainDataStoreRegistry { file ->
                        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file })
                    }
                val canonical = File(root, "logdate.preferences_pb")
                val alias = File(root, "alias/../logdate.preferences_pb")
                val stores = (0 until 20).map { index -> async { registry.get(if (index % 2 == 0) canonical else alias) } }.awaitAll()
                stores.forEach { assertSame(stores.first(), it) }
                val policy = stringPreferencesKey("security_level")
                stores.first().edit { it[policy] = "BIOMETRIC" }
                assertEquals("BIOMETRIC", stores.last().data.first()[policy])
            } finally {
                root.deleteRecursively()
            }
        }
}

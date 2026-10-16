package app.logdate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsUiContractTest {
    @Test
    fun `direct rows preserve section spacing`() {
        val source = "fun Example() { SettingsSection { SimpleSettingsItem(); SettingsNavigationItem() } }"
        assertTrue(SettingsUiContract.inspect(source).isEmpty())
    }

    @Test
    fun `layout wrappers around multiple rows lose section spacing`() {
        val source = "fun Example() { SettingsSection { Column { ExportDataItem(); ImportBackupItem() } } }"
        assertEquals(listOf(1), SettingsUiContract.inspect(source))
    }

    @Test
    fun `aliases and conditional rows cannot bypass the rule`() {
        val source = """
            import androidx.compose.foundation.layout.Column as Stack
            import app.logdate.ui.common.SimpleSettingsItem as Item
            fun Example() { SettingsSection { Stack { Item(); if (enabled) Item() } } }
        """.trimIndent()
        assertEquals(listOf(3), SettingsUiContract.inspect(source))
    }

    @Test
    fun `forms and text mentioning components are allowed`() {
        val source = """
            fun Example() {
                SettingsSection { Column { Text("SimpleSettingsItem()"); Slider() } }
                // SettingsSection { Column { SimpleSettingsItem(); SimpleSettingsItem() } }
            }
        """.trimIndent()
        assertTrue(SettingsUiContract.inspect(source).isEmpty())
    }

    @Test
    fun `named content uses the same grouping contract`() {
        val source = "fun Example() { SettingsSection(content = { Row { ToggleSettingsItem(); LinkedToggleSettingsItem() } }) }"
        assertEquals(listOf(1), SettingsUiContract.inspect(source))
    }

    @Test
    fun `one row inside a form is allowed`() {
        val source = "fun Example() { SettingsSection { Column { SimpleSettingsItem(); Text() } } }"
        assertTrue(SettingsUiContract.inspect(source).isEmpty())
    }

    @Test
    fun `settings implementations cannot bypass the shared row base`() {
        val source = """
            package app.logdate.feature.core.settings.ui
            import androidx.compose.material3.ListItem as RawRow
            fun Example() { RawRow(headlineContent = { Text("Title") }) }
        """.trimIndent()
        assertEquals(listOf(3), SettingsUiContract.inspect(source))
    }

    @Test
    fun `ordinary content lists outside settings remain allowed`() {
        val source = "package app.logdate.feature.timeline; fun Entries() { ListItem() }"
        assertTrue(SettingsUiContract.inspect(source).isEmpty())
    }

    @Test
    fun `supporting typography cannot override the shared role`() {
        val source = """
            package app.logdate.feature.core.settings.ui
            fun Example() { SettingsRow(supportingContent = { Text("Description", style = MaterialTheme.typography.bodySmall) }) }
        """.trimIndent()
        assertEquals(listOf(2), SettingsUiContract.inspect(source))
    }

    @Test
    fun `settings scaffolds outside a settings package use shared rows`() {
        val source = """
            package app.logdate.feature.events.ui.calendarsync
            fun Calendars() { SettingsScaffold { ListItem() } }
        """.trimIndent()
        assertEquals(listOf(2), SettingsUiContract.inspect(source))
    }

    @Test
    fun `boolean and choice controls use the shared functional wrappers`() {
        val source = """
            package app.logdate.feature.core.settings.ui
            fun Example() { Row { Text("Title"); Switch(); RadioButton() } }
        """.trimIndent()
        assertEquals(listOf(2), SettingsUiContract.inspect(source))
    }
}

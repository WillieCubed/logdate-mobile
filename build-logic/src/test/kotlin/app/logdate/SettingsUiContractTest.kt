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
                SettingsSection { Column { Text("SimpleSettingsItem()"); RadioButton() } }
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
}

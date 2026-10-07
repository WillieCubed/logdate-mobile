package app.logdate.feature.editor.ui.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class EditorCornerGeometryTest {
    @Test
    fun `nested cards and controls share the display corner center when geometry permits`() {
        val corners = editorCornerGeometry(48.dp)
        assertEquals(48.dp, 16.dp + corners.cardRadius)
        assertEquals(48.dp, 16.dp + 16.dp + corners.controlRadius)
    }

    @Test
    fun `card shape tracks the actual screen margin`() {
        assertEquals(40.dp, 24.dp + editorCornerGeometry(40.dp, edgeInset = 24.dp).cardRadius)
    }

    @Test
    fun `small or missing corners retain rounded squares and large corners cannot turn controls into circles`() {
        assertEquals(EditorCornerGeometry(8.dp, 8.dp), editorCornerGeometry(0.dp))
        assertEquals(EditorCornerGeometry(12.dp, 8.dp), editorCornerGeometry(Dp.Unspecified))
        assertEquals(16.dp, editorCornerGeometry(120.dp).controlRadius)
    }
}

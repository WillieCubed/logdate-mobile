package app.logdate.ui.workspace

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.WorkspaceSurface
import app.logdate.ui.theme.workspaceContainer
import app.logdate.ui.theme.workspaceContent
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceVisualContractTest {
    @Test fun selectionChangesTheItemMeaningRatherThanThePanelSurface() {
        val scheme = lightColorScheme()
        assertEquals(scheme.surface, scheme.workspaceContainer(WorkspaceSurface.Panel))
        assertEquals(scheme.onSurface, scheme.workspaceContent(WorkspaceSurface.Panel))
        assertEquals(scheme.secondaryContainer, scheme.workspaceContainer(WorkspaceSurface.Selected))
        assertEquals(scheme.surfaceContainerLow, scheme.workspaceContainer(WorkspaceSurface.Group))
        assertEquals(scheme.surfaceContainer, scheme.workspaceContainer(WorkspaceSurface.Canvas))
    }

    @Test fun attachmentShapesFlattenOnlyTheAttachedEdges() {
        val base = RoundedCornerShape(28.dp)
        val size = Size(400f, 800f)
        val density = Density(1f)
        val floating = panelShape(base, PanelPlacement.Floating)
        val attached = panelShape(base, PanelPlacement.EdgeAttached)
        val upper = panelShape(base, PanelPlacement.Upper)
        assertEquals(28f, floating.bottomStart.toPx(size, density))
        assertEquals(28f, attached.topStart.toPx(size, density))
        assertEquals(0f, attached.bottomStart.toPx(size, density))
        assertEquals(0f, upper.topStart.toPx(size, density))
        assertEquals(28f, upper.bottomStart.toPx(size, density))
    }
}

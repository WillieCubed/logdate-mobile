package app.logdate.ui.creation

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class EntryCreationBaseShapeTest {
    @Test
    fun compactSharedBaseResolvesTheActualFilledIconButtonOutlineAt48Dp() =
        runSkikoComposeUiTest {
            var resolved: List<Triple<Outline, Outline, Outline>> = emptyList()
            var expectedRadius = 0f
            setContent {
                LogDateTheme(dynamicColor = false) {
                    val density = LocalDensity.current
                    val extent = with(density) { 48.dp.toPx() }
                    val roundBase = entryCreationBaseShape(EntryCreationShape.Round)
                    val actualButtonShape = IconButtonDefaults.filledShape
                    val actualLargeShape = MaterialTheme.shapes.large
                    val outlines =
                        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).map { direction ->
                            val size = Size(extent, extent)
                            Triple(
                                roundBase.createOutline(size, direction, density),
                                actualButtonShape.createOutline(size, direction, density),
                                actualLargeShape.createOutline(size, direction, density),
                            )
                        }
                    SideEffect {
                        resolved = outlines
                        expectedRadius = extent / 2f
                    }
                }
            }
            waitForIdle()
            assertEquals(2, resolved.size)
            resolved.forEach { (shared, actualButton, large) ->
                val sharedRounded = assertIs<Outline.Rounded>(shared).roundRect
                val buttonRounded = assertIs<Outline.Rounded>(actualButton).roundRect
                assertEquals(buttonRounded, sharedRounded, "Compact source and destination must start from the actual button shape")
                assertNotEquals(
                    assertIs<Outline.Rounded>(large).roundRect,
                    sharedRounded,
                    "The fixture must distinguish large corners from CornerFull",
                )
                listOf(
                    sharedRounded.topLeftCornerRadius,
                    sharedRounded.topRightCornerRadius,
                    sharedRounded.bottomRightCornerRadius,
                    sharedRounded.bottomLeftCornerRadius,
                ).forEach { radius ->
                    assertEquals(expectedRadius, radius.x, 0.001f)
                    assertEquals(expectedRadius, radius.y, 0.001f)
                }
            }
        }
}
